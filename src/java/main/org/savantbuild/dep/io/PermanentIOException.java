/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
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
package org.savantbuild.dep.io;

import org.savantbuild.dep.DependencyException;
import org.savantbuild.dep.util.ErrorList;

/**
 * <p>
 * This exception denotes a permanant failure that will always occur
 * when performing the same IO operation multiple times.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class PermanentIOException extends DependencyException {
  public PermanentIOException() {
    super();
  }

  public PermanentIOException(String message) {
    super(message);
  }

  public PermanentIOException(String message, ErrorList errors) {
    super(message, errors);
  }

  public PermanentIOException(String message, Throwable cause) {
    super(message, cause);
  }

  public PermanentIOException(String message, Throwable cause, ErrorList errors) {
    super(message, cause, errors);
  }

  public PermanentIOException(Throwable cause) {
    super(cause);
  }

  public PermanentIOException(Throwable cause, ErrorList errors) {
    super(cause, errors);
  }
}
