/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import org.savantbuild.dep.domain.ResolvableItem;

/**
 * This class denotes that a negative cache was stored for an artifact item of some sort and that it should not be
 * resolved again.
 *
 * @author Brian Pontarelli
 */
public class NegativeCacheException extends ProcessFailureException {
  public NegativeCacheException(ResolvableItem item) {
    super(item);
  }
}
