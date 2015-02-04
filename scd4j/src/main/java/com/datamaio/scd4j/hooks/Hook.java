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
package com.datamaio.scd4j.hooks;

import static com.datamaio.scd4j.hooks.HookPreResult.CONTINUE;
import groovy.lang.Closure;
import groovy.lang.Script;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.datamaio.scd4j.cmd.Command;
import com.datamaio.scd4j.cmd.Interaction;
import com.datamaio.scd4j.cmd.linux.LinuxCommand;
import com.datamaio.scd4j.conf.Configuration;
import com.datamaio.scd4j.conf.Env;
import com.datamaio.scd4j.exception.DependencyNotFoundException;
import com.datamaio.scd4j.hooks.file.FileHook;
import com.datamaio.scd4j.hooks.module.ModuleHook;

/**
 * This class is the "father" of all hook scripts. A hook script is where you
 * can put your configuration/installation logic. In SCD4J we have two types of
 * hooks:
 * <ol>
 * <li> {@link ModuleHook}: one per module (optional)
 * <li> {@link FileHook}: one per file (optional)
 * </ol>
 * <p>
 * This class publishes the following:
 * <ul>
 * <li> {@link #pre(Closure)} and {@link #post(Closure)} methods. Those methods
 * may or may not be called into hook files in order to pre-define before and after
 * installation semantics, respectively
 * <li>Delegate and helper functions to be used inside of hook files
 * </ul>
 * 
 * @author Fernando Rubbo
 */
public abstract class Hook extends Script {
	private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	
	protected Configuration conf;
	protected Env envs;
	protected Map<String, Object> props;	
	protected Map<String, Object> temporaryProps;
	protected final Command command;	
	protected Closure<HookPreResult> pre;
	protected Closure<Void> post;

	/** Default constructor */
	public Hook() {
		this.command = Command.get();
	}
	
	//---------------- pre and post closure setters ---------------
	
	/**
	 * This method sets the closure that must be executed before the module/file
	 * installation
	 * <p>
	 * You must call this method whenever you need to:
	 * <ul>
	 * <li>Conditionally install a module or a file
	 * <li>Execute any programming logic before a module or a file installation.
	 * For example:
	 * <ul>
	 * <li>Execute different logic according to the environment in which you are
	 * running on (development, testing, staging and/or production)
	 * <li>Execute different logic depending on the operational system you are
	 * running on (Ubuntu, CentOS, Windows)
	 * <li>Stop a service (sometimes required to update a file)
	 * <li>and many others...
	 * </ul>
	 * </ul>
	 * 
	 * @param closure
	 *            The closure containing the pre-condition logic. <br>
	 *            Note that this closure must return an {@link HookPreResult}. If your
	 *            implementation does not return anything we assume
	 *            {@link HookPreResult#CONTINUE}
	 */
	protected final void pre(Closure<HookPreResult> closure) {
		this.pre = closure;
	}	

	/**
	 * This method sets the closure that must be executed after the module/file
	 * installation
	 * <p>
	 * You must call this method whenever you need to:
	 * <ul>
	 * <li>Execute any programming logic after installing a module or a file.
	 * For example:
	 * <ul>
	 * <li>Change the file permission
	 * <li>Rename or link the just installed file
	 * <li>Start up a service
	 * <li>and many others...
	 * </ul>
	 * </ul>
	 * 
	 * @param closure
	 *            The closure containing the post-condition logic.
	 */
	protected final void post(Closure<Void> closure){
		this.post = closure;
	}

	//---------------- helper and delegates methods ---------------
	
	/**
	 * Returns the name of the operational system 
	 * 
	 * @return The OS name (i.e. Linux or Windows)
	 */
	public String osname() {
		return command.osname();
	}
	
	/**
	 * Returns <code>true</code> if you are running on a Linux environment,
	 * false otherwise.
	 */
	public boolean isLinux() {
		return command.isLinux();
	}
	
	/**
	 * Returns <code>true</code> if you are running on a Windows environment,
	 * false otherwise.
	 */
	public boolean isWindows() {
		return command.isWindows();
	}

	/**
	 * Returns the distribution of the operational system
	 * <br>
	 * For example, it may return "CentOS", "Ubuntu" OR "Windows <version>"
	 */
	public String distribution() {
		return command.distribution();
	}
	
	/**
	 * Executes the given file <br>
	 * If the file is not executable, we try to make it so (Linux only). If it is not
	 * possible, an exception is thrown.
	 * 
	 * @param file
	 *            The executable file path
	 */
	public void execute(final String file) {
		command.execute(file);
	}
	
	/**
	 * Converts the given text file (usually a script file) into the pattern
	 * defined by the operational system that you are running on.
	 * <p>
	 * This is mostly required whenever you create a file on Windows and then
	 * run it on Linux, or vice and versa. Note: Usually configuration files are
	 * not an issue (because they are read by a program that already understand
	 * those difference), but executable scripts on Linux are!
	 * <p>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param textFile
	 *            The text file path
	 */
	public void fixText(final String textFile) {
		command.fixTextContent(textFile);
	}

	/**
	 * Creates a group of users. <br />
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param group
	 *            The name of the group.
	 */
	public void groupadd(final String group) {
		command.groupadd(group);
	}

	/**
	 * Creates a group of users with options. <br />
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param group
	 *            The name of the group.
	 * @param options
	 *            The same options you would like to use in the command line.
	 */
	public void groupadd(final String group, final String options) {
		command.groupadd(group, options);
	}

	/**
	 * Creates an user. <br />
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The user name.
	 */
	public void useradd(final String user) {
		command.useradd(user);
	}

	/**
	 * Creates an user with options. <br />
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The user name.
	 * @param options
	 *            The same options you would like to use in the command line.
	 */
	public void useradd(final String user, final String options) {
		command.useradd(user, options);
	}

	/**
	 * Deletes an existing user. <br />
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The user name.
	 */
	public void userdel(final String user) {
		command.userdel(user);
	}

	/**
	 * Deletes an existing user with options. <br />
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The user name.
	 * @param options
	 *            The same options you would like to use in the command line.
	 */
	public void userdel(final String user, final String options) {
		command.userdel(user, options);
	}
	
	/**
	 * Sets the user password. <br />
	 * <br />
	 * Important Notes:
	 * <ol>
	 * <li>Currently Linux only. Do nothing on Windows
	 * <li>if SELinux is turned on, this execution will fail
	 * <li>For Production environments it is a good practice to encrypt
	 * passwords in order to ensure that non authorized people could NOT read
	 * it. To accomplish that, use the command line
	 * <code>'gradlew encrypt'</code>. This will generate an encrypted
	 * property so that you can use it in your installation <br>
	 * In order to see other possibilities, type <code>'gradlew tasks'</code> in
	 * the command line. Probably you will be more interested in those which
	 * show up under <code>'Scd4j Tools tasks'</code> group
	 * </ol>
	 * 
	 * @param user
	 *            The user name.
	 * @param passwd
	 *            User's password.
	 */
	public void passwd(final String user, final String passwd) {
		command.passwd(user, passwd);
	}

	/**
	 * Changes the POSIX file permissions. It is not recursive.<br>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param mode
	 *            The POSIX definition (for example: "777").
	 * @param file
	 *            The file which you would like to change the permissions.
	 */
	public void chmod(final String mode, final String file) {
		command.chmod(mode, file);
	}

	/**
	 * Changes the POSIX file Permissions<br>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param mode
	 *            The POSIX definition (for example: "777")
	 * @param file
	 *            The file which you would like to change the permissions.
	 * @param recursive
	 *            If <code>true</code> apply the same rule for all sub
	 *            directories and files.
	 */
	public void chmod(final String mode, final String file, final boolean recursive) {
		command.chmod(mode, file, recursive);
	}

	/**
	 * Changes ownership of a file or a directory. It is not recursive.<br>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The new file's owner.
	 * @param path
	 *            The path (file or directory) to change the ownership
	 */
	public void chown(final String user, final String path) {
		command.chown(user, path);
	}
	
	/**
	 * Changes ownership of a file, possibly recursively. <br>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The new file's owner.
	 * @param path
	 *            The path (file or directory) to change the ownership.
	 * @param recursive
	 *            If <code>true</code> apply the same rule for all sub
	 *            directories and files.
	 */
	public void chown(final String user, final String path, final boolean recursive) {
		command.chown(user, path, recursive);
	}
	
	/**
	 * Changes ownership of a file. It is not recursive.<br>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The new file's owner.
	 * @param group
	 *            The new group.
	 * @param path
	 *            The path (file or directory) to change ownership.
	 */
	public void chown(final String user, final String group, final String path) {
		command.chown(user, path, path);
	}

	/**
	 * Changes ownership of a file, possibly recursively<br>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param user
	 *            The new file's owner.
	 * @param group
	 *            The new group.
	 * @param path
	 *            The path (file or directory) to change ownership.
	 * @param recursive
	 *            If <code>true</code> apply the same rule for all sub
	 *            directories and files.
	 */
	public void chown(final String user, final String group, final String path, final boolean recursive) {
		command.chown(user, group, path, recursive);
	}

	/**
	 * DSL for {@link #ln(String, String)}. <br />
	 * <p>
	 * How to use this DSL:
	 * <pre>
	 * link "/etc/init.d/my_text_link" to "/opt/test/my_existing_file"
	 * </pre>
	 */
	public Destination link(final String link) {
		return new Destination() {
			@Override
			public void to(String targetFile) {
				ln(link, targetFile);
			}
		};		
	}
	
	/**
	 * Create a symbolic link <br>
	 * Note: Currently Linux Only. Do nothing on Windows
	 * 
	 * @param link
	 *            The link path.
	 * @param targetFile
	 *            The target path (file or directory) to be linked.
	 */
	public void ln(final String link, final String targetFile) {
		command.ln(link, targetFile);
	}

	/**
	 * Returns the current user name
	 */
	public String whoami() {
		return command.whoami();
	}
	
	/**
	 * Checks if the given path (file or directory) exists
	 * 
	 * @param file
	 *            The target path (file or directory).
	 * @return <code>true</code> if the file exists, <code>false</code>
	 *         otherwise
	 */
	public boolean exists(final String file) {
		return command.exists(file);
	}

	/**
	 * Creates a directory and all nonexistent parent directories first
	 * 
	 * @param dir
	 *            Directory path to create.
	 */
	public void mkdir(final String dir) {
		command.mkdir(dir);
	}
	
	/** 
	 * DSL for {@link #rm(String)} 
	 * <p>
	 * How to use this DSL:
	 * <pre>
	 * remove "/opt/test/my_existing_file" 
	 * remove "/opt/test/my_existing_dir"
	 * </pre>
	 */
	public void remove(final String path) {
		rm(path);
	}

	/**
	 * Remove a file or a directory. This method is recursive, in other words,
	 * if you remove a directory it will remove all its subdirectories
	 * 
	 * @param path
	 *            the path of the file or of the directory to be exclude
	 */
	public void rm(final String path) {
		command.rm(path);
	}

	/** 
	 * DSL for {@link #mv(String, String)}
	 * <p>
	 * How to use this DSL:
	 * <pre>
	 * move "/opt/test/my_existing_file" to "/opt/test/new_name" 
	 * move "/opt/test/my_existing_dir" to "/opt/test/new_name"
	 * </pre> 
	 */
	public Destination move(final String from) {
		return new Destination() {
			@Override
			public void to(String to) {
				mv(from, to);	
			}
		};		
	}

	/**
	 * Move a origin path (file or a directory) to another path
	 * 
	 * @param from
	 *            Accepts a file or a directory
	 * @param to
	 *            The destination directory or file. If it does not exists, it
	 *            will be created
	 */
	public void mv(final String from, final String to) {
		command.mv(from, to);
	}
	
	/** 
	 * DSL for {@link #cp(String, String)}
	 * <p> 
	 * How to use this DSL:
	 * <pre>
	 * copy "/opt/test/my_existing_file" to "/opt/test2" 
	 * copy "/opt/test/my_existing_dir" to "/opt/test2"
	 * </pre>  
	 */
	public Destination copy(final String from) {
		return new Destination() {
			@Override
			public void to(String to) {
				cp(from, to);
			}
		};
	}
	
	/**
	 * Copy a file or a directory from a destination to another
	 * 
	 * @param from
	 *            Accepts a file, a directory or a dependency
	 * @param to
	 *            The destination directory or file. If it does not exists, it
	 *            will be created as an directory 
	 */
	public void cp(final String from, final String to) {
		String newFrom = from;
		if (!Files.exists(Paths.get(from))) {
			try {
				newFrom = resolve(from);
			} catch (DependencyNotFoundException e) {
				throw new RuntimeException("It was not possible to find file/dir '" + from + "' to copy!");
			}
		}
		
		if(!exists(to)){
			mkdir(to);
		}
		command.cp(newFrom, to);
	}
	
	/** 
	 * DSL for {@link #ls(String)}
	 * <p>
	 * How to use this DSL:
	 * <pre>
	 * def files = list "/opt/test" 
	 * </pre>
	 */
	public List<String> list(final String path) {
		return ls(path);
	}
	
	/**
	 * List the entries (files and directories) of a given directory path
	 * 
	 * @param path
	 *            the path to list entries
	 * @return List of all files and directories matched
	 */
	public List<String> ls(final String path) {
		return command.ls(path);
	}

	// --- run ----

	/**
	 * Execute a command line <br>
	 * Note: Operational System dependent
	 * 
	 * @param cmd
	 *            the command line
	 */
	public String run(String cmd) {
		return command.run(cmd);
	}

	/**
	 * Execute a command line <br>
	 * Note: Operational System dependent
	 * 
	 * @param cmd
	 *            the command line
	 * @param printOutput
	 *            you can opt to not print the command output in the logs
	 *            providing <code>false</code> to this parameter
	 */
	public String run(String cmd, final boolean printOutput) {
		return command.run(cmd, printOutput);
	}

	/**
	 * Execute a command line <br>
	 * Note: Operational System dependent
	 * 
	 * @param cmd
	 *            the command line
	 * @param successfulExec
	 *            a list of successful results. The Linux default success result
	 *            is 0.
	 */
	public String run(String cmd, final int... successfulExec) {
		return command.run(cmd, successfulExec);
	}

	/**
	 * Execute a command line <br>
	 * Note.1: Operational System dependent<br>
	 * Note.2: In Linux, if SELinux is turned on, this execution will always
	 * fail
	 * 
	 * @param cmd
	 *            the command line
	 * @param interact
	 *            allow you to programmatically interact with the process
	 *            similarly in the way a user would do it. See
	 *            {@link Interaction} for more implementation details.
	 */
	public String run(String cmd, Interaction interact) {
		return command.run(cmd, interact);
	}

	/**
	 * This method does no interaction at all. In other words, it will only show
	 * the output after the process has finish.
	 * <p>
	 * Important Notes:
	 * <ol>
	 * <li>This method was created because, in very rare executions in Linux,
	 * the process hangs reading output. We are still figuring out the issue,
	 * but we are expecting to resolve this with Java 9 new Process API<br>
	 * <li>If possible use {@link #run(String)} variant methods once this one
	 * may be removed in future releases
	 * </ol>
	 * 
	 * @param cmd
	 *            the command line
	 */
	public String runWithNoInteraction(String cmd) {
		return command.runWithNoInteraction(cmd);
	}

    // --- env methods ---

	/**
	 * Returns <code>true</code> if the environment is development<br>
	 * In other words, if it is NOT Testing, NOT Staging
	 * and NOT Production environment.
	 * 
	 * @see {@link #isTesting()}, {@link #isStaging()}, {@link #isProduction()}
	 */
	public boolean isDevelopment(){
		return !isTesting() && !isStaging() && !isProduction();
	}

	/** 
	 * Returns <code>true</code> if the environment is Testing<br>
	 * This is configured in the build.gradle file.
	 * <p>
	 * For example:
	 * <pre>
	 * scd4j {
	 * 	...
	 * 	env {
	 * 		...
	 * 		testing = ["192.168.3.20", "192.168.3.21"] 
	 * 	}
	 * }
	 * </pre>
	 * 
	 * <br>
	 * Important Notes:
	 * <ol>
	 * 	<li>In order to make this method work as expected the name of the
	 * machine must be in the DNS and you must have an entry in the /etc/hosts
	 * with ip 127.0.0.1 using the same name you have used in DNS
	 * 	<li>None of env ips are required. So if you have a very simple
	 * environment, just ignore it. Everything will be development
	 * </ol>
	 */
	public boolean isTesting(){
		final String address = whatIsMyIp();
		return envs.isTesting(address);
	}

	/** 
	 * Returns <code>true</code> if the environment is Staging<br>
	 * This is configured in the build.gradle file.
	 * <p>
	 * For example:
	 * <pre>
	 * scd4j {
	 * 	...
	 * 	env {
	 * 		...
	 * 		staging  = ["192.168.7.20", "192.168.7.21"]
	 * 	}
	 * }
	 * </pre>
	 * 
	 * <br>
	 * Important Notes:
	 * <ol>
	 * 	<li>In order to make this method work as expected the name of the
	 * machine must be in the DNS and you must have an entry in the /etc/hosts
	 * with ip 127.0.0.1 using the same name you have used in DNS
	 * 	<li>None of env ips are required. So if you have a very simple
	 * environment, just ignore it. Everything will be development
	 * </ol>
	 */
	public boolean isStaging(){
		final String address = whatIsMyIp();
		return envs.isStagging(address);
	}

	/**
	 * Returns <code>true</code> if the environment is Production<br>
	 * This is configured in the build.gradle file.
	 * <p>
	 * For example:
	 * 
	 * <pre>
	 * scd4j {
	 * 	...
	 * 	env {
	 * 		production = ["192.168.10.20", "192.168.10.21", "192.168.10.22", "192.168.10.23"]
	 * 		staging    = ["192.168.7.20", "192.168.7.21"]
	 * 		testing    = ["192.168.3.20", "192.168.3.21"] 
	 * 	}
	 * }
	 * </pre>
	 * 
	 * <br>
	 * Important Notes:
	 * <ol>
	 * 	<li>In order to make this method work as expected the name of the
	 * machine must be in the DNS and you must have an entry in the /etc/hosts
	 * with ip 127.0.0.1 using the same name you have used in DNS
	 * 	<li>None of env ips are required. So if you have a very simple
	 * environment, just ignore it. Everything will be development
	 * </ol>
	 */
	public boolean isProduction(){
		final String address = whatIsMyIp();
		return envs.isProduction(address);
	}

	/**
	 * Try to discover what is the machine IP.
	 * <p>
	 * Note that this method has unexpected behavior if you have more than one network card/ip 
	 * 
	 * @return the ip address
	 */
	private String ip = null;
    public String whatIsMyIp()
    {
    	if(ip==null) {
	        try {
	        	ip = getLocalHostLANAddress().getHostAddress().trim();
			} catch (UnknownHostException e) {
				throw new RuntimeException(e);
			}
    	}
    	return ip;
    }
        
	/**
	 * Try to discover what is the machine host name.
	 * <p>
	 * Note that this method has unexpected behavior if you have more than one network card/ip
	 *  
	 * @return the ip address
	 */
    public String whatIsMyHostName()
    {
        try {
			final InetAddress addr = InetAddress.getLocalHost();
	        return addr.getHostName();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
    }

    /**
	 * Use this method for every log content. So it will be stored in the dir
	 * log<br>
	 * Please, do not use println. Otherwise you will print the log information
	 * only in the standard output and you will will lose it if you close the console.
	 */
    public void log(String msg) {
        LOGGER.info("\t" + msg);
    }
        
    // --- properties methods ---
    
    /**
     * EXPERIMENTAL - DO NOT USE IT:
     * <br>
	 * Sets a temporary ({@link #setTempProperty(String, Object)}) or a
	 * permanent ({@link #setPermanentProperty(String, Object)}) property.<br>
	 *  
	 * <p>
	 * How to use this DSL:
	 * 
	 * <pre>
	 * set temporary: "my_key" with: "my_string_value"
	 * OR
	 * set temporary: "my_key" with: true
	 * OR
	 * set temporary: "my_key" with: 234
	 * OR
	 * set temporary: "my_key" with: 98.23
	 * 
	 * =======
	 * 
	 * set permanent: "my_key" with: "my_string_value"
	 * OR
	 * set permanent: "my_key" with: true
	 * OR
	 * set permanent: "my_key" with: 234
	 * OR
	 * set permanent: "my_key" with: 98.23	 
	 * </pre>
	 */
    public void set(Map<String, ? extends Object> values){
    	Object value = values.get("with");
    	if(value==null){
    		throw new RuntimeException("Incorrect use of 'set' try: set temporary: \"MY_KEY\", with:\"MY_VALUE\"");
    	}
    	
    	String keyTemporary = (String)values.get("temporary");
    	if(keyTemporary!=null) {
    		setTempProperty(keyTemporary, value);
    	} else {
    		String keyPermanent = (String)values.get("permanent");
    		if(keyPermanent!=null) {
    			setPermanentProperty(keyPermanent, value);	
    		} else {
    			throw new RuntimeException("Incorrect use of 'set' try: set temporary: \"MY_KEY\", with:\"MY_VALUE\"");
    		}
    	}
    }

    /**
     * EXPERIMENTAL - DO NOT USE IT:
     * <br>
	 * Sets a temporary ({@link #setTempProperty(String, Object)}) or a
	 * permanent ({@link #setPermanentProperty(String, Object)}) property.<br>
	 *  
	 * <p>
	 * How to use this DSL:
	 * 
	 * <pre>
	 * set "my_key" with "my_string_value" _as temp
	 * OR
	 * set "my_key" with true _as temp
	 * OR
	 * set "my_key" with 234 _as temp
	 * OR
	 * set "my_key" with 98.23 _as temp
	 * 
	 * =======
	 *
	 * set "my_key" with "my_string_value" _as permanent
	 * OR
	 * set "my_key" with true _as permanent
	 * OR
	 * set "my_key" with 234  _as permanent
	 * OR
	 * set "my_key" with 98.23  _as permanent
	 * </pre>
	 */
    public SetValue set(String key){
    	return new SetValue(){
			@Override
			public SetDuration with(String value) {
				return new SetDuration(){
					@Override
					public void _as(PropertyDuration d) {
						if(PropertyDuration.permanent.equals(d)){
							setPermanentProperty(key, value);
						} else {
							setTempProperty(key, value);
						}
					}
				};
			}
			@Override
			public SetDuration with(Boolean value) {
				return new SetDuration(){
					@Override
					public void _as(PropertyDuration d) {
						if(PropertyDuration.permanent.equals(d)){
							setPermanentProperty(key, ""+value);
						} else {
							setTempProperty(key, ""+value);
						}						
					}
				};
			}
			@Override
			public SetDuration with(Integer value) {
				return new SetDuration(){
					@Override
					public void _as(PropertyDuration d) {
						if(PropertyDuration.permanent.equals(d)){
							setPermanentProperty(key, ""+value);
						} else {
							setTempProperty(key, ""+value);
						}						
					}
				};
			}
			@Override
			public SetDuration with(Double value) {
				return new SetDuration(){
					@Override
					public void _as(PropertyDuration d) {
						if(PropertyDuration.permanent.equals(d)){
							setPermanentProperty(key, ""+value);
						} else {
							setTempProperty(key, ""+value);
						}						
					}
				};
			}    		
    	};
    }
    protected PropertyDuration permanent = PropertyDuration.permanent;
    protected PropertyDuration temp = PropertyDuration.temp;    
    enum PropertyDuration {permanent, temp}
    protected interface SetDuration{
    	void _as(PropertyDuration d); //TODO: can not use 'as' because it is a keyword in groovy
    }
    protected interface SetValue {
		SetDuration with(String value);
		SetDuration with(Boolean value);
		SetDuration with(Integer value);
		SetDuration with(Double value);
	}  
       
	/**
	 * Sets a temporary/transient property.<br>
	 * A temporary/transient property remains set only for a short period of time. In
	 * other words, if it is set in Hook#pre() method, it will remain until the
	 * end of Hook#post() method execution.
	 */
    public void setTempProperty(String key, Object value) {
		props.put(key, value);
		temporaryProps.put(key, value);
    }

	/**
	 * Sets a permanent/persistent property.<br>
	 * A permanent/persistent property remains up until the end of the program execution<br>
	 */
    public void setPermanentProperty(String key, Object value) {
		props.put(key, value);
    }
    
    /** 
     * Returns the value of a property.<br>
	 * Properties can be set through programming (see
	 * {@link #setTempProperty(String, Object)} and {@link #setPermanentProperty(String,
	 * Object)} methods) or via configuration file in <code>build.gradle</code>:
     * 
     * <pre>
	 * scd4j {
	 * 	install {
	 * 		... 
	 * 		config = "my_config_file.conf"
	 * 	}
	 * }
	 * </pre>
     */
    public Object get(final String key){
		if(props==null)
			return null;
	    return props.get(key);
	}

    /**
	 * Checks if a given key was set as a property.<br>
	 * Properties can be set through programming (see
	 * {@link #setTempProperty(String, Object)} and {@link #setPermanentProperty(String,
	 * Object)} methods) or via configuration file in <code>build.gradle</code>:
	 * 
	 * <pre>
	 * scd4j {
	 * 	install {
	 * 		... 
	 * 		config = "my_config_file.conf"
	 * 	}
	 * }
	 * </pre>
	 */
    public boolean contains(final String key){
        return props.get(key)!=null;
    }
	

	// --- install methods ---

	/**
	 * Configures a given service to start at system boot<br>
	 * This method used default levels for each operational system
	 *   	
	 * @param serviceName the name of the service
	 */
	public void registryOnBoot(String serviceName) {
		command.startServiceAtSystemBoot(serviceName);
	}

	/**
	 * Configures a given service to not start at system boot<br>
	 * This method used default levels for each operational system
	 *   	
	 * @param serviceName the name of the service
	 */
	public void unregistryFromBoot(String serviceName) {
		command.doNotStartServiceAtSystemBoot(serviceName);
	}
	
	/**
	 * Install or update a package.
	 * <p>
	 * This method is capable to install three types of packages depending on the parameter it receives:
	 * <ul>
	 * 
	 * 	<li> Install a package (rpm, deb or an executable file on windows) which is located inside of scd4j directory.
	 * 		<br>
	 * 		In order to accomplish that, you need to configure the dependencies in build.gradle.
	 * 		<br>
	 * 		For example:
	 * 		<pre>
	 * 	dependencies {
	 * 		scd4j files('dependencies/my_dependency.deb')
	 * 	}
	 * 		</pre>
	 * 		Then, in your hook file, you will need to call. Note that, at this point, you must provide only the file name.
	 * 		<pre>
	 * 	install "my_dependency.deb"
	 * 		</pre>
	 * 
	 * 	<li> Install a package (rpm, deb or an executable file on windows) which is located inside of your Artifactory or Nexus binary repository.
	 * 		<br>
	 * 		In order to accomplish that, you need to configure the dependencies in build.gradle.
	 * 		<br>
	 * 		For example:
	 * 		<pre>
	 * 	dependencies {
	 * 		scd4j 'my_group:my_pack_name:my_pack_version@rpm',
	 * 	}
	 * 		</pre>
 	 *		Then, in your hook file, you will need to call
	 * 		<pre>
	 * 	install 'my_group:my_pack_name:my_pack_version@rpm'
	 * 		</pre>
	 * 
	 * 	<li> Install an operational system package (not supported on Windows)
	 * 		<br>
	 * 		In this case, there is no need to configure the dependency in build.gradle<br>
	 * 		For example, in your hook file you only will need to call:
	 * 		<pre>
	 * 		install "lxde=0.5.0-4ubuntu4"
	 * 		</pre>
	 * 		It is a good practice to provide the package name and the version you are going to install:
	 * 		<ul>
	 * 			<li>Use "=" to separate the package and the version in Ubuntu: "lxde=0.5.0-4ubuntu4"
	 * 			<li>Use "-" to separate the package and the version in Ubuntu: "lxde-0.5.0"
	 * 		</ul> 		
	 * </ol>
	 * 
	 * @param pack the package name
	 */
	public void install(String pack) {
		try { 
			String path = resolve(pack);
			installLocalPack(path);
		} catch (DependencyNotFoundException e) {		
			installRemotePack(pack);
		}
	}

	void installLocalPack(String path) {
		command.installLocalPack(path);
	}
	
	void installRemotePack(String pack) {
		command.installRemotePack(pack);
	}

	/** 
	 * Uninstall an existing package.
	 * <p>
	 * This method is capable to uninstall two types of packages depending on the parameter it receives:
	 * <ul>
	 * 
	 * 	<li> Uninstall a package (rpm or deb) which was installed locally
	 * 		<br>
	 * 		In order to accomplish that, in your hook file, you will need to call
	 * 		<br>
	 * 		<pre>
	 * 		uninstall 'foo'
	 * 		</pre>
	 * 		Where foo is the name of your deb package
	 * 
	 * 	<li> Uninstall a package (rpm or deb) which was installed from the OS repositories
	 * 		<br>
	 * 		In order to accomplish that, in your hook file, you will need to call
	 * 		<pre>
	 * 		uninstall 'lxde'
	 * 		</pre>
	 * 		Where lxde is the name of your package in the OS repository
	 * 
	 * </ol>
	 * <p>
	 * --
	 * <p>
	 * Note: Currently only supports Linux operational system packages uninstall 
	 * 
	 * @param pack the package name without the version
	 */
	public void uninstall(String pack) {
		if(command.isInstalled(pack)){
			try {
				uninstallLocalPack(pack);
			} catch (Exception e) {
				e.printStackTrace();
				// TODO: It would not be better to uninstall using the same strategy than installation?
				// I'm asking this because packs can be unistalling with uninstallLocalPack even though they was installed using apt-get install, for example
				// As far as I know, behind the scenes it uses the same command.. need to be sure.
				uninstallRemotePack(pack);
			}
		}
	}
	
	void uninstallLocalPack(String pack) {
		command.uninstallLocalPack(pack);
	}

	void uninstallRemotePack(String pack) {
		command.uninstallRemotePack(pack);
	}
		
	/**
	 * DSL for {@link #unzip(String, String)}
	 * <p>
	 * How to use this DSL:
	 * <pre>
	 * 	unzip "org.wildfly:wildfly:8.1.0.Final@zip" to "/opt/example_wildfly_dir"
	 * 	unzip "my_dependency.zip" to "/opt/my_dependency_dir"
	 * 	unzip "my_other_dependency.zip" to "/opt/my_other_dependency_dir"
	 * </pre>
	 */	
	public Destination unzip(String depName) {
		return new Destination() {			
			@Override
			public void to(String dir) {
				unzip(depName, dir);				
			}
		};
	}
	
	/**
	 * Unzip a dependency which in in a public url, in the Artifactory, Nexus or
	 * which is located inside of scd4j directory.<br>
	 * In order to accomplish that, you need to configure the dependency in
	 * build.gradle.
	 * <p>
	 * 
	 * For example:
	 * 
	 * <pre>
	 * dependencies {
	 * 	scd4j 'org.wildfly:wildfly:8.1.0.Final@zip',
	 * 	files('dependencies/my_dependency.zip'),
	 * 	url('http://mysite.com/xxx/my_other_dependency.zip')
	 * }
	 * </pre>
	 * 
	 * Then, in your hook file, you will need to call
	 * 
	 * <pre>
	 * 	unzip("org.wildfly:wildfly:8.1.0.Final@zip", "/opt/example_wildfly_dir")
	 * 	unzip("my_dependency.zip", "/opt/another_example_dir")
	 * 	unzip("my_other_dependency.zip", "/opt/my_other_dependency_dir")
	 * </pre>
	 * 
	 * Note that the first line will get the dependency from a binary repository
	 * (Artifactory or Nexus), the second line will get it from a scd4j relative
	 * directory and the third will get it directly from an url
	 * <p>
	 * Important Note: We encourage to use <code>url(..)</code> only in the following cases:
	 * <ol>
	 * 	<li>If the location is an internal site and you are a 100% sure it will never change 
	 * 	<li>OR for testing purpose only
	 * </ol>
	 * 
	 * @param depName
	 *            the dependency name as shown in above examples
	 * @param doDir
	 *            The destination directory. If it does not exists, it
	 *            will be created
	 */	 
	public void unzip(String depName, String toDir) {
		String from = resolve(depName);
		if(!exists(toDir)){
			mkdir(toDir);
		}
		command.unzip(from, toDir);				
	}
	
	/**
	 * Downloads (uses cache to avoid downloading the same dependency many
	 * times) a dependency which was put in the relative scd4j folder, in an url
	 * or in your binary repository (i.e. Artifactory or Nexus). <br>
	 * In order to accomplish that, you need to configure the dependency in
	 * build.gradle.
	 * <p>
	 * For example:
	 * 
	 * <pre>
	 * dependencies {
	 * 	scd4j 'org.wildfly:wildfly:8.1.0.Final@zip',
	 * 		'com.xyz:foo:0.1@deb',
	 * 		'com.xyz:oof:1.0@rpm',
	 * 		files('dependencies/my_dependency.zip'),
	 * 		url('http://mysite.com/xxx/my_other_dependency.zip')
	 * }
	 * </pre>
	 * 
	 * Then, in you hook file, you will need to call
	 * 
	 * <pre>
	 * def path = resolve "org.wildfly:wildfly:8.1.0.Final@zip"
	 * OR
	 * def path = resolve "com.xyz:foo:0.1@deb"
	 * OR
	 * def path = resolve "com.xyz:oof:1.0@rpm"
	 * OR
	 * def path = resolve "my_dependency.zip" 
	 * OR
	 * def path = resolve "my_other_dependency.zip"
	 * </pre>
	 * <p>
	 * 
	 * For dependencies in your binary repository, if you do not provide the
	 * file extension (@deb, @rpm, @zip, @ear, @war or
	 * 
	 * @jar) this method will try to infer it according to the following rule:
	 *       <ol>
	 *       <li>Tries to resolve the full provided dependency name <li>If it
	 *       does not work and you are running on Linux, tries to resolve the
	 *       distribution dependency (i.e. @deb for ubuntu and @rpm for CentOs)
	 *       <li>If it does not work tries to resolve the dependency in the
	 *       following order: @zip,
	 * @ear, @war or @jar
	 *       </ol>
	 * 
	 *       Although this method is capable to resolve the file extension, we
	 *       strongly recommend to always provide it.
	 * <p>
	 * 
	 * @param depName
	 *            the full dependency name
	 * @return the full path where is located the file
	 */		
	public String resolve(final String depName) {
    	Path file = conf.getDependency(depName);
    	
    	if(file==null && isLinux()){
			String packExtension = ((LinuxCommand)command).getPackExtension();
			file = conf.getDependency(depName + "@" + packExtension);	   		
    	}
    	
    	if(file==null) {
	    	List<String> extentions = Arrays.asList("zip", "ear", "war", "jar");
	    	String ext = extentions.stream()
	    		.filter(e -> conf.getDependency(depName + "@" + e)!=null )	    		
	    		.findFirst().orElse(null);
	    	file = conf.getDependency(depName + "@" + ext);
    	}
    	    	
    	if(file==null)
    		throw new DependencyNotFoundException("Could not resolve dependency: " + depName);

    	return file.toString();
    }
    
    // --- services ---

    /** 
     * Starts an Operational System service if it is not running yet
	 *  
     * @param name the service name
     * @throws RuntimeException if the service does not exists
     */
	public void start(String name) {		
    	command.serviceStart(name);
	}
    
    /** 
     * Stops an Operational System service if it is running.
	 *  
     * @param name the service name
     * @throws RuntimeException if the service does not exists 
     */
	public void stop(String name) {
		command.serviceStop(name);
	}

    /** 
     * restart an Operational System service
	 *  
     * @param name the service name
     * @throws RuntimeException if the service does not exists
     */
	public void restart(String name) {
		command.serviceRestart(name);
	}
	
	/**
	 * Checks the Operational System service status<br>
	 * This method does not throw an exception if the service does not exists.
	 * 
	 * @param name
	 *            the service name
	 * @return status information of the service (in lowercase), or the error message if it
	 *         does not exists
	 */
	public String status(String name) {
		return command.serviceStatus(name);
	}

	// ------ methods used by the framework only ----
	
    /**
     * Not a public API.<br> 
     * Used only by {@link HookEvaluator} to set variables 
     */
	final void setConf(Configuration conf) {
		this.conf = conf;
		this.envs = conf.getEnv();
		this.props = conf.getProps();
		this.temporaryProps = conf.getTempProps();
	}
	
	
	/**
     * Not a public API.<br> 
	 * Used only by {@link HookEvaluator} to call the pre condition closure 
	 */
	final HookPreResult _pre(){
		if (pre != null) {
			HookPreResult hookPreResult = pre.call();
			if (hookPreResult == null) { 
				// pre{} does not return anything				
				return CONTINUE;
			} 
			
			validateReturningAction(hookPreResult);
			return hookPreResult;	
		}
		
		// pre{} was not defined
		return CONTINUE;
	}

	/**
     * Not a public API.<br>   
	 */
	protected abstract void validateReturningAction(HookPreResult hookPreResult);
	
	/**
     * Not a public API.<br> 
	 * Used only by {@link HookEvaluator} to call the post condition closure 
	 */
	final void _post() {
		if(post!=null){
			post.call(); 
		}
	}	
	
	/**
     * Not a public API.<br> 
	 * Used only by {@link HookEvaluator} to cleanup transient properties 
	 */
	final void _finish() {
		temporaryProps.forEach((k, v) -> props.remove(k) );
		temporaryProps.clear();
	}
	
	// ---- interfaces for dsl -----
	
	protected interface Destination {		
		void to(String to);
	}
	
	// ------ private methods ------

	/**
	 * FROM: discussion https://issues.apache.org/jira/browse/JCS-40
	 * 
     * Returns an <code>InetAddress</code> object encapsulating what is most likely the machine's LAN IP address.
     * <p/>
     * This method is intended for use as a replacement of JDK method <code>InetAddress.getLocalHost</code>, because
     * that method is ambiguous on Linux systems. Linux systems enumerate the loopback network interface the same
     * way as regular LAN network interfaces, but the JDK <code>InetAddress.getLocalHost</code> method does not
     * specify the algorithm used to select the address returned under such circumstances, and will often return the
     * loopback address, which is not valid for network communication. Details
     * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037">here</a>.
     * <p/>
     * This method will scan all IP addresses on all network interfaces on the host machine to determine the IP address
     * most likely to be the machine's LAN address. If the machine has multiple IP addresses, this method will prefer
     * a site-local IP address (e.g. 192.168.x.x or 10.10.x.x, usually IPv4) if the machine has one (and will return the
     * first site-local address if the machine has more than one), but if the machine does not hold a site-local
     * address, this method will return simply the first non-loopback address found (IPv4 or IPv6).
     * <p/>
     * If this method cannot find a non-loopback address using this selection algorithm, it will fall back to
     * calling and returning the result of JDK method <code>InetAddress.getLocalHost</code>.
     * <p/>
     *
     * @throws UnknownHostException If the LAN address of the machine cannot be found.
     */
    private static InetAddress getLocalHostLANAddress() throws UnknownHostException {
        try {
            InetAddress candidateAddress = null;
            // Iterate all NICs (network interface cards)...
            for (Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
                NetworkInterface iface = ifaces.nextElement();
                // Iterate all IP addresses assigned to each card...
                for (Enumeration<InetAddress> inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements();) {
                    InetAddress inetAddr = inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {

                        if (inetAddr.isSiteLocalAddress()) {
                            // Found non-loopback site-local address. Return it immediately...
                            return inetAddr;
                        }
                        else if (candidateAddress == null) {
                            // Found non-loopback address, but not necessarily site-local.
                            // Store it as a candidate to be returned if site-local address is not subsequently found...
                            candidateAddress = inetAddr;
                            // Note that we don't repeatedly assign non-loopback non-site-local addresses as candidates,
                            // only the first. For subsequent iterations, candidate will be non-null.
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                // We did not find a site-local address, but we found some other non-loopback address.
                // Server might have a non-site-local address assigned to its NIC (or it might be running
                // IPv6 which deprecates the "site-local" concept).
                // Return this non-loopback candidate address...
                return candidateAddress;
            }
            // At this point, we did not find a non-loopback address.
            // Fall back to returning whatever InetAddress.getLocalHost() returns...
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return jdkSuppliedAddress;
        }
        catch (Exception e) {
            UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}
