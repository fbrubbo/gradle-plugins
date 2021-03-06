/**
 * The MIT License (MIT)
 *
 * Copyright (C) 2014 scd4j scd4j.tools@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datamaio.scd4j;

import static com.datamaio.scd4j.conf.Configuration.build;
import static java.nio.file.Files.exists;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.datamaio.scd4j.conf.Configuration;
import com.datamaio.scd4j.util.BackupHelper;
import com.datamaio.scd4j.util.LogHelper;
import com.datamaio.scd4j.util.io.FileUtils;
import com.datamaio.scd4j.util.io.PathUtils;

/**
 * @author Fernando Rubbo
 * @author Mateus M. da Costa
 */
public class EnvConfiguratorTest {
	
	@After
	public void teardown(){
		LogHelper.closeAndRemoveFileHandler();
		Path base = new File(".").getAbsoluteFile().toPath();
		FileUtils.delete(PathUtils.get(base, "backup"));
		FileUtils.delete(PathUtils.get(base, "log"));
	}
	
	@Test
	public void testDelete() throws Exception {
		Path[] paths = createEnv(1);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		
		try {
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir2")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir1/f1.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "ff.txt")), is(false));
			
			new EnvConfiguratorMock(build(module)).deleteFiles();
	
			assertThat(exists(PathUtils.get(fs, "dir1/f1.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir2")), is(false));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(false));
			
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "ff.txt")), is(false));
		} finally {
			FileUtils.delete(root);
		}
	}

	@Test
	public void testCopyWithoutTmpl() throws Exception {
		Path[] paths = createEnv(2);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		
		try {
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir2/f2.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir1/f1.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "ff.txt")), is(true));
			
			new EnvConfiguratorMock(build(module)).copyFiles();
	
			assertThat(exists(PathUtils.get(fs, "dir1/f1.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "ff.txt")), is(true));
			
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(true));
		} finally {
			FileUtils.delete(root);
		}
	}
	
	@Test
	public void testCopyWithTmpl() throws Exception {
		Path[] paths = createEnv(3);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		Path result = paths[3];
		
		try {
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir2/f2.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir1/f1.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "ff.txt")), is(true));
			
			Map<String, Object> props = new HashMap<>();
			props.put("favlang", "aaaaaa");
			props.put("favlang2", "bbbbbb");
			new EnvConfiguratorMock(build(module, props)).copyFiles();		
	
			checkFileContent(fs, result, "dir1/f1.txt");
			checkFileContent(fs, result, "dir2/dir21/f21.txt");
			checkFileContent(fs, result, "f.txt");
			checkFileContent(fs, result, "ff.txt");
			checkFileContent(fs, result, "dir3/f3.txt");
		} finally {
			FileUtils.delete(root);
		}
	}
	

	@Test
	public void testExecWithTmplAndDelete() throws Exception {
		Path[] paths = createEnv(4);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		Path result = paths[3];
		
		try {
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir2/f2.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir1/f1.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "ff.txt")), is(true));
			
			Map<String, Object> props = new HashMap<>();
			props.put("favlang", "aaaaaa");
			props.put("favlang2", "bbbbbb");
			new EnvConfiguratorMock(build(module, props)).execute();		
	
			checkFileContent(fs, result, "dir1/f1.txt");
			checkFileContent(fs, result, "dir2/dir21/f21.txt");
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(false));
			checkFileContent(fs, result, "f.txt");
			checkFileContent(fs, result, "ff.txt");
		} finally {
			FileUtils.delete(root);
		}
	}
	
	@Test
	public void testExecWithFilePreCondition() throws Exception {
		Path[] paths = createEnv(5);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		
		try{ 
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(true));
			
			Map<String, Object> props = new HashMap<>();
			props.put("favlang", "aaaaaa");
			new EnvConfiguratorMock(build(module, props)).execute();		
	
			assertThat(exists(PathUtils.get(fs, "dir2/dir21/f21.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(true));
		} finally {		
			FileUtils.delete(root);
		}
	}
	
	@Test
	public void testExecWithFilePostCondition() throws Exception {
		Path[] paths = createEnv(6);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		
		try {
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(true));	
			Set<PosixFilePermission> before = null;
			
			if (!isWindows()) {
				before = Files.getPosixFilePermissions(PathUtils.get(fs, "f.txt"));
			}
			
			assertThat(exists(PathUtils.get(fs, "ff.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "ff.txt.postexecuted")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(true));
			
			Map<String, Object> props = new HashMap<>();
			props.put("favlang", "aaaaaa");
			props.put("favlang2", "bbbbbb");
			new EnvConfiguratorMock(build(module, props)).execute();		
	
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(true));
			
			if (!isWindows()) {
				Set<PosixFilePermission> after = Files.getPosixFilePermissions(PathUtils.get(fs, "f.txt"));
				assertThat(after, is(not(equalTo(before))));
			}
			assertThat(exists(PathUtils.get(fs, "ff.txt.postexecuted")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt.postexecuted")), is(true));
		} finally {		
			FileUtils.delete(root);
		}
	}
	
	@Test
	public void testExecWithModulePreCondition() throws Exception {
		Path[] paths = createEnv(7);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		Files.write(PathUtils.get(module, "Module.hook"), buildModuleHookPre()); 

		try {
			Map<String, Object> props = new HashMap<>();
			props.put("var", "var errada");
			new EnvConfiguratorMock(build(module, props)).execute();		
	
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(false));
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(false));
		} finally {		
			FileUtils.delete(root);
		}
	}
	
	@Test
	public void testExecWithExecModulePostCondition() throws Exception {
		Path[] paths = createEnv(8);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		Files.write(PathUtils.get(module, "Module.hook"), buildModuleHookPost()); 

		try {
			new EnvConfiguratorMock(build(module)).execute();		
	
			assertThat(exists(PathUtils.get(fs, "f.txt")), is(true));
			assertThat(exists(PathUtils.get(fs, "dir3/f3.txt")), is(true));
			assertThat(exists(PathUtils.get(module, "Module.postexecuted")), is(true));		
		} finally {		
			FileUtils.delete(root);
		}
	}
	
	/**
	 * Issue: https://jira.codehaus.org/browse/GROOVY-2939
	 * Using GroovyTemplate, we need to encode in .tmpl files:
	 * - all '\' as '\\' 
	 * - all '$' as '\$' (not because of the issue, but because it is a char to execute the EL)  
	 */
	@Test
	public void testGroovyTmplFilesWithSpecialChars() throws Exception {
		Path[] paths = createEnv(9);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		Path result = paths[3];
		
		try {
			assertThat(exists(PathUtils.get(fs, "f1.txt")), is(false));
			
			new EnvConfiguratorMock(build(module)).execute();		
	
			assertThat(exists(PathUtils.get(fs, "f1.txt")), is(true));
			checkFileContent(fs, result, "f1.txt");
		} finally {
			FileUtils.delete(root);
		}
	}
	
	/**
	 * Issue: https://jira.codehaus.org/browse/GROOVY-2939
	 * Using GroovyTemplate, we need to encode in .tmpl files:
	 * - all '\' as '\\' 
	 * - all '$' as '\$' (not because of the issue, but because it is a char to execute the EL)  
	 */	
	@Test
	public void testGroovyTmplFilesWithElExpression() throws IOException, URISyntaxException {
		Path[] paths = createEnv(10);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		Path result = paths[3];
		
		try {
			Map<String, Object> props = new HashMap<>();
			props.put("test", "TESTADO!");
			new EnvConfiguratorMock(build(module, props)).execute();
			assertThat(exists(PathUtils.get(fs, "dir1/f10.txt")), is(true));
			checkFileContent(fs, result, "dir1/f10.txt");
		} finally {
			FileUtils.delete(root);
		}
	}
	
	@Test
	public void tesPathVariable() throws Exception{
		Path[] paths = createEnv(11);
		Path root = paths[0];
		Path fs = paths[1];
		Path module = paths[2];
		
		try {
			Map<String, Object> props = new HashMap<>();
			props.put("var", "test");
			assertThat(exists(PathUtils.get(fs, "dirtest/filetest.txt")), is(false));
			new EnvConfiguratorMock(build(module, props)).execute();			
			assertThat(exists(PathUtils.get(fs, "dirtest/filetest.txt")), is(true));
		} finally {
			FileUtils.delete(root);
		}
	}
	
	// ------------ private methods ---------
	
	private byte[] buildModuleHookPre() {
		return ("pre {"
				+ "\n	if (\"xyz\".equals(get(\"var\")))"
				+ "\n		CONTINUE;"
				+ "\n	else"
				+ "\n		ABORT;"
				+ "\n}").getBytes();
	}
	
	private byte[] buildModuleHookPost() {
		return ("\npost {"
				+ "\n	Files.createFile(Paths.get(moduleDir + \"/Module.postexecuted\"));"
				+ "\n}").getBytes();
	}

	private Path[] createEnv(int index) throws IOException, URISyntaxException {
		Path root = null;				
		if (isWindows()) {
			root = buildRootPathForWindows();
		} else {
			root =  Files.createTempDirectory("root");
		}
				
		Path targetFileSystemDir = FileUtils.createDirectories(PathUtils.get(root, "fs"));
		Path tempModuleDir = FileUtils.createDirectories(PathUtils.get(root, "modules"));
		Path targetResultDir = FileUtils.createDirectories(PathUtils.get(root, "result"));
		Path targetModuleDir = FileUtils.createDirectories(PathUtils.get(tempModuleDir, targetFileSystemDir));
		
		FileUtils.copy(getFileSystemResource(index), targetFileSystemDir);
		FileUtils.copy(getModuleResource(index), targetModuleDir);
		FileUtils.copy(getResultResource(index), targetResultDir);
		
		return new Path[]{root, targetFileSystemDir, tempModuleDir, targetResultDir};
	}
	
	private Path buildRootPathForWindows() throws IOException {
		Path root = null;
		Path newRootDir = Files.createTempDirectory("root");
		for (Path rootPath : newRootDir.getFileSystem().getRootDirectories()) {
			if (newRootDir.startsWith(rootPath)) {
				root = Paths.get("/" + rootPath.relativize(newRootDir).toString());
			}
		}
		return root;
	}

	private Path getFileSystemResource(int index) throws URISyntaxException {		
		String resource = "/com/datamaio/scd4j/EnvConfiguratorResources/filesystem/fs" + index;
		return getResource(resource);
	}
	
	private Path getModuleResource(int index) throws URISyntaxException {
		String resource = "/com/datamaio/scd4j/EnvConfiguratorResources/modules/m" + index;
		return getResource(resource);
	}

	private Path getResultResource(int index) throws URISyntaxException {
		String resource = "/com/datamaio/scd4j/EnvConfiguratorResources/result/r" + index;
		return getResource(resource);
	}
	
	private Path getResource(String resource) throws URISyntaxException {
		return Paths.get(getClass().getResource(resource).toURI());
	}
	
	private void checkFileContent(Path fs, Path result, String file) {
		assertThat(exists(PathUtils.get(fs, file)), is(true));
		try {
			byte[] actual = Files.readAllBytes(PathUtils.get(fs, file));
			byte[] expected = Files.readAllBytes(PathUtils.get(result, file));
			Assert.assertThat(actual, is(equalTo(expected)));
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	private boolean isWindows() {
		String os = System.getProperty("os.name");
		return os.toUpperCase().contains("WINDOWS");
	}
	
	public static class EnvConfiguratorMock extends EnvConfigurator{
		public EnvConfiguratorMock(Configuration conf) {
			super(conf);
		}

		@Override
		BackupHelper buildBackupHelper(Configuration conf) {
			return new BackupHelper(conf){
				public void backupFile(Path file) {};
				public void backupFileOrDir(Path fileOrDir) {};
				public void init(Configuration conf) {};
			};
		}		
		
		@Override
		LogHelper buildLogHelper(Configuration conf) {
			return new LogHelper(conf){
				private final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
				@Override
				public void startup() {
					System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %1$tb %1$td, %1$tY %1$tH:%1$tM:%1$tS %5$s%6$s%n");

					// avoid tha the handler be registred more than once
					closeAndRemoveFileHandler();

			        // registry the handlers
			        LOGGER.setLevel(Level.INFO);
			        LOGGER.setUseParentHandlers(false);

			        ConsoleHandler consoleHandler = new ConsoleHandler();
			        consoleHandler.setFormatter(new SimpleFormatter());
			        LOGGER.addHandler(consoleHandler);
			    }				
			};
		}
	}
}
