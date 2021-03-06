/*
 * Copyright 2013 Jive Software, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.jivesoftware.os.upena.service;

import com.jivesoftware.os.upena.service.UpenaMap.UpenaKeyProvider;
import com.jivesoftware.os.upena.shared.Permission;
import com.jivesoftware.os.upena.shared.PermissionKey;

import java.nio.charset.StandardCharsets;

public class PermissionKeyProvider implements UpenaKeyProvider<PermissionKey, Permission> {

    @Override
    public PermissionKey getNodeKey(UpenaMap<PermissionKey, Permission> table, Permission value) {
        return forgePermissionKey(value.permission);
    }

    public static PermissionKey forgePermissionKey(String permission) {
        String k = Long.toString(Math.abs(
            new JenkinsHash().hash(permission.getBytes(StandardCharsets.UTF_8), 2)));
        return new PermissionKey(k);
    }

}
