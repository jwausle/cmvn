package de.tototec.tools.cmvn

import de.tototec.cmdoption.CmdCommand
import de.tototec.cmdoption.CmdOption

@CmdCommand(names = Array("--convert-pom"), description = "Generate Cmvn project configuration from Maven pom.xml files")
class PomConverterCmd extends HelpAwareCmd {
  @CmdOption(names = Array("--pom-file"), args = Array("FILE"), description = "Read an alternativ Maven project file")
  var pomFile: String = "pom.xml"

  @CmdOption(names = Array("--cmvn-file"), args = Array("FILE"), description = "Write an alternativ Cmvn project file")
  var cmvnFile: String = "cmvn.conf"

  @CmdOption(names = Array("--modules-are-cmvn"), description = "Assume subprojects (modules) are Cmvn projects already")
  var modulesAreCmvn: Boolean = false

  @CmdOption(names = Array("--dry-run"), description = "Do not write/modify anything, just display the result")
  var dryRun: Boolean = false
}