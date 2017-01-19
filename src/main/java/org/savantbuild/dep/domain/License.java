/*
 * Copyright (c) 2013-2017, Inversoft Inc., All Rights Reserved
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

package org.savantbuild.dep.domain;

/**
 * Enumeration for licenses.
 *
 * @author Brian Pontarelli
 */
public enum License {
  ApacheV1_0(false),

  ApacheV1_1(false),

  ApacheV2_0(false),

  BSD(true),

  BSD_2_Clause(true),

  BSD_3_Clause(true),

  BSD_4_Clause(true),

  CDDLV1_0(false),

  CDDLV1_1(false),

  Commercial(true),

  EclipseV1_0(false),

  GPLV1_0(false),

  GPLV2_0(false),

  GPLV2_0_CE(false),

  GPLV3_0(false),

  LGPLV2_1(false),

  LGPLV3_0(false),

  MIT(true),

  Other(true),

  OtherDistributableOpenSource(true),

  OtherNonDistributableOpenSource(true),

  Public_Domain(false);

  public final boolean requiresText;

  License(boolean requiresText) {
    this.requiresText = requiresText;
  }
}
