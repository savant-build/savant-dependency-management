/*
 * Copyright (c) 2001-2011, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep;

import java.io.File;
import java.net.MalformedURLException;

import static org.savantbuild.TestTools.*;

/**
 * <p>
 * This class is an abstract base class for tests that provides helper methods.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class AbstractDependencyTest {
  /**
   * Sets up a simple workflow that fetches via URLs and caches to the target dir.
   *
   * @param root The root for the URls.
   * @return The workflow.
   */
  protected Workflow makeWorkflow(File root) {
    try {
      Workflow w = new Workflow();
      w.getFetchProcesses().add(new Process(map("type", "url", "url", root.toURI().toURL().toString())));
      w.getPublishProcesses().add(new Process(map("type", "cache", "dir", "target/test/deps")));
      return w;
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

}
