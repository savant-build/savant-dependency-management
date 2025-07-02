/*
 * Copyright (c) 2025, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep.workflow.process;

import java.nio.file.Path;

import org.savantbuild.dep.domain.ResolvableItem;
import org.savantbuild.dep.workflow.PublishWorkflow;

/**
 * Do not publish just return the provided item.
 *
 * @author Daniel DeGroff
 */
public class DoNotPublishProcess implements Process {
  @Override
  public Path fetch(ResolvableItem item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path publish(ResolvableItem item, Path itemFile) throws ProcessFailureException {
    return itemFile;
  }
}
