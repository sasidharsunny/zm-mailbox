/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2012, 2013, 2014 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class FileUtilTest {

    private List<File> tempPaths = Lists.newArrayList();

    /**
     * Overrides {@code lastModified} to return system time.  {@code File.lastModified()} may
     * return values in second-level granularity, which causes the unit test to fail.
     */
    @SuppressWarnings("serial")
    class VirtualFile extends File {
        final long lastModified = System.currentTimeMillis();

        VirtualFile(String name) {
            super(name);
        }

        @Override
        public long lastModified() {
            return lastModified;
        }
    }

    @Test
    public void sortByMtime() throws InterruptedException {
        List<File> files = Lists.newArrayList();
        File temp1 = new VirtualFile("temp1");
        files.add(temp1);
        Thread.sleep(50);

        File temp2 = new VirtualFile("temp2");
        files.add(temp2);

        assertTrue(temp1.lastModified() != temp2.lastModified());

        FileUtil.sortFilesByModifiedTime(files, true);
        assertEquals(temp2, files.get(0));
        FileUtil.sortFilesByModifiedTime(files);
        assertEquals(temp1, files.get(0));
    }

    @Test
    public void deleteDir() throws IOException {
        File tempDir = createTempDir();
        FileUtil.deleteDir(tempDir);
        assertFalse(tempDir.exists());
    }

    @Test
    public void deleteDirContents() throws IOException {
        File tempDir = createTempDir();
        FileUtil.deleteDirContents(tempDir);
        assertTrue(tempDir.exists());
        assertEquals(0, tempDir.listFiles().length);
    }

    private File createTempDir() throws IOException {
        File tempDir = Files.createTempDir();
        tempPaths.add(tempDir);
        File.createTempFile("FileUtilTest", null, tempDir);
        File childDir = new File(tempDir, "child");
        assertTrue(childDir.mkdir());
        File.createTempFile("FileUtilTest", null, childDir);
        return tempDir;
    }

    @After
    public void tearDown() throws IOException {
        for (File file : tempPaths) {
            if (file.isFile()) {
                file.delete();
            } else {
                FileUtil.deleteDir(file);
            }
        }
    }
}
