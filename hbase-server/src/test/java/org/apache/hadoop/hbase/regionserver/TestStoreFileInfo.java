/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.regionserver;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtil;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.io.HFileLink;
import org.apache.hadoop.hbase.io.Reference;
import org.apache.hadoop.hbase.io.hfile.ReaderContext;
import org.apache.hadoop.hbase.io.hfile.ReaderContext.ReaderType;
import org.apache.hadoop.hbase.regionserver.storefiletracker.StoreFileTrackerForTest;
import org.apache.hadoop.hbase.testclassification.RegionServerTests;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test HStoreFile
 */
@Category({ RegionServerTests.class, SmallTests.class })
public class TestStoreFileInfo {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestStoreFileInfo.class);

  private static final HBaseTestingUtil TEST_UTIL = new HBaseTestingUtil();

  /**
   * Validate that we can handle valid tables with '.', '_', and '-' chars.
   */
  @Test
  public void testStoreFileNames() {
    String[] legalHFileLink = { "MyTable_02=abc012-def345", "MyTable_02.300=abc012-def345",
      "MyTable_02-400=abc012-def345", "MyTable_02-400.200=abc012-def345",
      "MyTable_02=abc012-def345_SeqId_1_", "MyTable_02=abc012-def345_SeqId_20_" };
    for (String name : legalHFileLink) {
      assertTrue("should be a valid link: " + name, HFileLink.isHFileLink(name));
      assertTrue("should be a valid StoreFile" + name, StoreFileInfo.validateStoreFileName(name));
      assertFalse("should not be a valid reference: " + name, StoreFileInfo.isReference(name));

      String refName = name + ".6789";
      assertTrue("should be a valid link reference: " + refName,
        StoreFileInfo.isReference(refName));
      assertTrue("should be a valid StoreFile" + refName,
        StoreFileInfo.validateStoreFileName(refName));
    }

    String[] illegalHFileLink = { ".MyTable_02=abc012-def345", "-MyTable_02.300=abc012-def345",
      "MyTable_02-400=abc0_12-def345", "MyTable_02-400.200=abc012-def345...." };
    for (String name : illegalHFileLink) {
      assertFalse("should not be a valid link: " + name, HFileLink.isHFileLink(name));
    }
  }

  @Test
  public void testEqualsWithLink() throws IOException {
    Path origin = new Path("/origin");
    Path tmp = TEST_UTIL.getDataTestDir();
    Path mob = new Path("/mob");
    Path archive = new Path("/archive");
    HFileLink link1 = new HFileLink(new Path(origin, "f1"), new Path(tmp, "f1"),
      new Path(mob, "f1"), new Path(archive, "f1"));
    HFileLink link2 = new HFileLink(new Path(origin, "f1"), new Path(tmp, "f1"),
      new Path(mob, "f1"), new Path(archive, "f1"));

    StoreFileInfo info1 =
      new StoreFileInfo(TEST_UTIL.getConfiguration(), TEST_UTIL.getTestFileSystem(), null, link1);
    StoreFileInfo info2 =
      new StoreFileInfo(TEST_UTIL.getConfiguration(), TEST_UTIL.getTestFileSystem(), null, link2);

    assertEquals(info1, info2);
    assertEquals(info1.hashCode(), info2.hashCode());
  }

  @Test
  public void testOpenErrorMessageReference() throws IOException {
    // Test file link exception
    // Try to open nonsense hfilelink. Make sure exception is from HFileLink.
    Path p = new Path(TEST_UTIL.getDataTestDirOnTestFS(), "4567.abcd");
    FileSystem fs = FileSystem.get(TEST_UTIL.getConfiguration());
    fs.mkdirs(p.getParent());
    Reference r = Reference.createBottomReference(HConstants.EMPTY_START_ROW);
    RegionInfo regionInfo = RegionInfoBuilder.newBuilder(TableName.valueOf("table1")).build();
    StoreContext storeContext = StoreContext.getBuilder()
      .withRegionFileSystem(HRegionFileSystem.create(TEST_UTIL.getConfiguration(), fs,
        TEST_UTIL.getDataTestDirOnTestFS(), regionInfo))
      .withColumnFamilyDescriptor(
        ColumnFamilyDescriptorBuilder.newBuilder("cf1".getBytes()).build())
      .build();
    StoreFileTrackerForTest storeFileTrackerForTest =
      new StoreFileTrackerForTest(TEST_UTIL.getConfiguration(), true, storeContext);
    storeFileTrackerForTest.createReference(r, p);
    StoreFileInfo sfi = storeFileTrackerForTest.getStoreFileInfo(p, true);
    try {
      ReaderContext context = sfi.createReaderContext(false, 1000, ReaderType.PREAD);
      sfi.createReader(context, null);
      throw new IllegalStateException();
    } catch (FileNotFoundException fnfe) {
      assertTrue(fnfe.getMessage().contains("->"));
    }
  }
}
