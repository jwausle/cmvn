package de.tototec.cmvn.eclipse.plugin

import java.io.File
import scala.Array.canBuildFrom
import scala.collection.JavaConversions.asScalaBuffer
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import de.tototec.cmvn.model.Dependency
import de.tototec.cmvn.ConfiguredCmvnProject

object CmvnClasspathContainer {

  val ContainerName = """de.tototec.cmvn.CMVN_DEPENDENCIES"""
  val Legacy018ContainerName = """de.tototec.tools.cmvn.CMVN_DEPENDENCIES"""

  val ReplaceContainerOnChanges = true

}

class CmvnClasspathContainer(path: IPath, private val project: IJavaProject, private val updateContainerAction: (CmvnClasspathContainer) => Unit) extends IClasspathContainer {

  def this(copy: CmvnClasspathContainer) {
    this(copy.getPath, copy.project, copy.updateContainerAction)
    debug("Creating new CmvnClasspathContainer from old one for project " + project)
    this._cmvnProject = copy._cmvnProject
    this._cmvnFileTimestamp = copy._cmvnFileTimestamp
    this.classpathEntries = classpathEntries
  }

  override val getKind = IClasspathContainer.K_APPLICATION
  override val getDescription = "Cmvn Libraries"
  override val getPath = path

  protected val projectRootFile: File = project.getProject.getLocation.makeAbsolute.toFile

  protected val settings: Settings = new Settings(path)

  protected def debug(msg: => String) {
    Console.println(msg)
  }

  protected def computeClasspathEntries(cmvn: ConfiguredCmvnProject): Array[IClasspathEntry] = {
    debug("computeClasspathEntries(cmvn=" + (if (cmvn != null) "..." else "null") + ") for project=" + project + " and containerPath=" + path)

    val workspaceProjects = JavaCore.create(project.getProject.getWorkspace.getRoot).getJavaProjects

    def asWorkspaceDep(dep: Dependency): Option[IJavaProject] = {
      settings.workspaceResolution match {
        case true =>
          workspaceProjects.find(p => {
            val depPath = p.getPath
            //        debug("Checking workspace project " + p + " with path " + depPath)
            p.exists && depPath.segmentCount() == 1 && depPath.segment(0) == dep.artifactId
          })
        case _ => None
      }
    }

    val scopes = settings.readScopes
    val deps: List[Dependency] = cmvn.projectConfig.dependencies.toList.distinct.filter { dep =>
      scopes.exists(_ == dep.scope)
    }

    deps.map { dep =>
      try {
        asWorkspaceDep(dep) match {
          case Some(workspaceProject) =>
            JavaCore.newProjectEntry(workspaceProject.getPath)
          case _ => {
            var needM2Var = false

            // Create reference to maven repo
            val jarPath = dep.jarPath match {
              case p: String => new File(p).isAbsolute match {
                case true => p
                case false => project.getProject.getRawLocation.toFile.getPath + File.separator + p
              }
              case _ => {
                var localRepoPathPrefix = cmvn.configuredState.localRepository
                if (localRepoPathPrefix != null && localRepoPathPrefix != "") {
                  localRepoPathPrefix = new File(localRepoPathPrefix).getAbsolutePath
                } else {
                  localRepoPathPrefix = "M2_REPO"
                  needM2Var = true
                }
                dep.mavenJarLocalRepoPath(localRepoPathPrefix)
              }
            }

            val sourcePath = if (jarPath.toLowerCase().endsWith(".jar"))
              new Path(jarPath.substring(0, jarPath.length() - 4) + "-sources.jar")
            else null

            if (needM2Var) {
              JavaCore.newVariableEntry(new Path(jarPath), sourcePath, null)
            } else {
              JavaCore.newLibraryEntry(new Path(jarPath), sourcePath, null)
            }
          }
        }
      } catch {
        case e: Exception =>
          debug("Skipping dependency: " + dep + ". Reason: Caught an exception: " + e + "\n" + e.getStackTrace().mkString("\n\t"))
          null
      }
    }.filter(_ != null).toArray
  }

  private val cmvnFile = new File(projectRootFile, "cmvn.conf")
  private var _cmvnFileTimestamp: Long = 0L
  private var _cmvnProject: ConfiguredCmvnProject = _
  protected def cmvnProject: ConfiguredCmvnProject = {
    if (cmvnFile.exists) {
      if (_cmvnProject == null || cmvnFile.lastModified > _cmvnFileTimestamp) {
        debug((if (_cmvnProject != null) "Reloading" else "Loading") + " CmvnProject from " + cmvnFile)
        _cmvnFileTimestamp = cmvnFile.lastModified
        try {
          _cmvnProject = new ConfiguredCmvnProject(cmvnFile, relaxedVersionCheck = true)
        } catch {
          case e: RuntimeException =>
            debug("Could not create project. Caught RuntimeException: " + e.getLocalizedMessage)
        }
      }
      _cmvnProject
    } else {
      debug("Could not found CmvnProject at " + cmvnFile)
      _cmvnProject = null
      null
    }
  }

  private var classpathEntries: Array[IClasspathEntry] = _

  override def getClasspathEntries: Array[IClasspathEntry] = {
    cmvnProject match {
      case null => {
        debug("No CmvnProject found for project " + project)
        Array()
      }
      case cmvnProject => CmvnClasspathContainer.ReplaceContainerOnChanges match {
        case false =>
          // Always use up-to-date list
          computeClasspathEntries(cmvnProject)

        case true => {
          val oldCpEntries = classpathEntries
          classpathEntries = computeClasspathEntries(cmvnProject)

          // TODO: check if we should first remove this CP Container before (re-)adding to force updates in raw classpath
          if (oldCpEntries != null) {
            if (classpathEntries.size != oldCpEntries.size ||
              (classpathEntries, oldCpEntries).zipped.exists(_ != _)) {
              // Reset the CP-Container if the classpath has changed
              debug("Classpath has changed from\n - old cp: " + oldCpEntries.mkString("\n   - ") + "\n - new cp: " + classpathEntries.mkString("\n   - "))
              updateContainerAction(this)
            }
          }

          classpathEntries
        }
      }
    }
  }

}
