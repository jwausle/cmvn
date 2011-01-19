package de.tototec.tools.cmvn.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;

import de.tototec.tools.cmvn.Version;

import lombok.Data;

@Data
public class ConfigClassGenerator {

	private final String targetDir;
	private final String className;
	private final Map<String, String> methodProperties;

	public void generateClass(final File baseDir) {
		File targetBaseDir = new File(this.targetDir);
		if (!targetBaseDir.isAbsolute()) {
			targetBaseDir = new File(baseDir, this.targetDir);
		}
		targetBaseDir.mkdirs();

		String packageName = null;
		String className = null;

		final int lastIndexOf = this.className.lastIndexOf(".");
		if (lastIndexOf == -1) {
			// Generate into default package!
			packageName = null;
			className = this.className;
		} else {
			packageName = this.className.substring(0, lastIndexOf);
			className = this.className.substring(lastIndexOf + 1);
		}

		File classDir = targetBaseDir;

		if (packageName != null) {
			for (final String packDir : packageName.split("\\.")) {
				classDir = new File(classDir, packDir);
			}
		}
		classDir.mkdirs();

		final File classFile = new File(classDir, className + ".java");
		System.out.println("Generating " + classFile);

		try {
			final PrintWriter writer = new PrintWriter(classFile);
			if (packageName != null) {
				writer.append("package ").append(packageName).append(";\n\n");
			}
			writer.append("// Class generated by cmvn-" + Version.cmvnVersion() + "\n");
			writer.append("public abstract class ").append(className).append(" {\n\n");

			for (final Entry<String, String> method : methodProperties.entrySet()) {
				writer.append("\tpublic static String ").append(method.getKey()).append("() {\n");
				writer.append("\t\treturn \"").append(method.getValue()).append("\";\n\t}\n\n");
			}

			writer.append("}\n");

			writer.close();

		} catch (final FileNotFoundException e) {
			throw new RuntimeException("Could not generate version class: " + this, e);
		}
	}
}
