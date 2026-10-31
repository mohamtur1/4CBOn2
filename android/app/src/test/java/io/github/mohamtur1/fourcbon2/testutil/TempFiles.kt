package io.github.mohamtur1.fourcbon2.testutil

import java.io.File
import java.nio.file.Files

fun createTempDirectory(prefix: String): File = Files.createTempDirectory(prefix).toFile()
