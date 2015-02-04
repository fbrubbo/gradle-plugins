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
package com.datamaio.scd4j.cmd.linux.debian;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.datamaio.scd4j.cmd.linux.LinuxCommand;

/**
 * 
 * @author Fernando Rubbo
 */
public abstract class DebBaseCommand  extends LinuxCommand {
	private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	public static final String INST_EXTENSION = "deb";
	
	@Override
	public String getPackExtension(){
		return INST_EXTENSION;
	}
	
	@Override
	public void startServiceAtSystemBoot(String serviceName) {
		run("update-rc.d " + serviceName + " defaults");
	}
	
	@Override
	public void doNotStartServiceAtSystemBoot(String serviceName) {
		run("update-rc.d -f " + serviceName + " remove");
	}
	
	/**
	 * Use "=" to separate the package and the version in Ubuntu: "lxde=0.5.0-4ubuntu4"
	 * 
	 * USe apt-cache showpkg <pachagename> to show the options
	 */	
	@Override
	public void installRemotePack(String pack, String version) {
		LOGGER.info("\tInstalling package " + pack + (version!=null? " ("+version+")" : ""));
		String fullpack = pack + (version!=null? "=" + version : "");
		List<String> cmd = Arrays.asList(new String[] { "apt-get", "-y", "install", fullpack });
		run(cmd);
	}	
	
	@Override	
	public void installLocalPack(String path) {
		LOGGER.info("\tInstalling DEB File from " + path + " ... ");
		run("dpkg -i " + path);
	}
	
	@Override
	public boolean isInstalled(String pack) {
		try {
			run("dpkg-query -l " + pack, false);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	@Override
	public void uninstallRemotePack(String pack) {
		LOGGER.info("\tRemoving package " + pack + " and dependencies");
		run("apt-get -y --auto-remove purge " + pack);
	}
	
	@Override
	public void uninstallLocalPack(String pack) {
		LOGGER.info("\tUninstalling DEB File from " + pack + " ... ");
		run("dpkg --purge " + pack);
		run("apt-get -y autoremove");
	}
	
	@Override
	public void addRepository(String repository) {
		run("add-apt-repository -y " + repository);
		run("apt-get update", false);
	}
}
