# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import sys

def _SetupTVCMPath():
  tvcm_path = os.path.abspath(os.path.join(os.path.dirname(__file__),
                                           'third_party', 'tvcm'))
  if tvcm_path not in sys.path:
    sys.path.append(tvcm_path)
  vinn_path = os.path.abspath(os.path.join(os.path.dirname(__file__),
                                           'third_party', 'vinn'))
  if vinn_path not in sys.path:
    sys.path.append(vinn_path)

_SetupTVCMPath()
