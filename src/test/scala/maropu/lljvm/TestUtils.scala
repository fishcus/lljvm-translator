/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package maropu.lljvm

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

import jasmin.ClassFile
import org.scalatest.FunSuite

object TestUtils extends FunSuite {

  def doTest(id: String, f: String, sig: Seq[Class[_]], args: Seq[AnyRef], expected: Any): Unit = {
    val clazz = TestUtils.loadClassFromResource(id)
    try {
      val method = clazz.newInstance.getClass.getMethod(f, sig: _*)
      assert(method.invoke(null, args: _*) === expected)
    } catch {
      case e: Throwable =>
        fail(
          s"""Illegal bytecode found: ${e.getMessage}
             |${LLJVMUtils.asBytecode(TestUtils.resourceToBytes(id))}
           """.stripMargin)
    }
  }

  def compareCode(actual: String, expected: String): Unit = {
    val normalize = (s: String) =>
      s.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "")
    assert(normalize(actual) === normalize(expected))
  }

  def loadClassFromBytecode(className: String, bytecode: Array[Byte]): Class[_] = {
    val clazz = new LLJVMClassLoader().loadClassFromBytecode(className, bytecode)
    assert(clazz.getCanonicalName === className)
    clazz
  }

  def loadClassFromResource(location: String): Class[_] = {
    val bitcode = TestUtils.resourceToBytes(location)
    val clazz = new LLJVMClassLoader().loadClassFromBitcode("GeneratedClass", bitcode)
    assert(clazz.getCanonicalName === "GeneratedClass")
    clazz
  }

  /**
   * Create a directory inside the given parent directory. The directory is guaranteed to be
   * newly created, and is not marked for automatic deletion.
   */
  def createDirectory(root: String, namePrefix: String = "lljvm"): File = {
    var attempts = 0
    val maxAttempts = 10
    var dir: File = null
    while (dir == null) {
      attempts += 1
      if (attempts > maxAttempts) {
        throw new IOException("Failed to create a temp directory (under " + root + ") after " +
          maxAttempts + " attempts!")
      }
      try {
        dir = new File(root, namePrefix + "-" + UUID.randomUUID.toString)
        if (dir.exists() || !dir.mkdirs()) {
          dir = null
        }
      } catch { case e: SecurityException => dir = null; }
    }

    dir.getCanonicalFile
  }

  /**
   * Create a temporary directory inside the given parent directory. The directory will be
   * automatically deleted when the VM shuts down.
   */
  def createTempDir(
      root: String = System.getProperty("java.io.tmpdir"),
      namePrefix: String = "lljvm"): File = {
    val dir = createDirectory(root, namePrefix)
    dir.deleteOnExit()
    dir
  }

  def resourceToBytes(resource: String): Array[Byte] = {
    val inStream = Thread.currentThread.getContextClassLoader.getResourceAsStream(resource)
    val outStream = new ByteArrayOutputStream
    try {
      var reading = true
      while (reading) {
        inStream.read() match {
          case -1 => reading = false
          case c => outStream.write(c)
        }
      }
      outStream.flush()
    } finally {
      inStream.close()
    }
    outStream.toByteArray
  }
}
