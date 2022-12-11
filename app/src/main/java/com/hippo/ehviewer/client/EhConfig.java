/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

/**
 * Some configurable stuff about EH. It ends up cookie.
 */
public class EhConfig implements Cloneable {

    /**
     * The Cookie key of uconfig
     */
    public static final String KEY_UCONFIG = "uconfig";
    /**
     * The Cookie key of show warning
     */
    public static final String KEY_CONTENT_WARNING = "nw";
    /**
     * Not show warning
     */
    public static final String CONTENT_WARNING_NOT_SHOW = "1";
    /**
     * Cateories
     */
    public static final int MISC = 0x1;
    public static final int DOUJINSHI = 0x2;
    public static final int MANGA = 0x4;
    public static final int ARTIST_CG = 0x8;
    public static final int GAME_CG = 0x10;
    public static final int IMAGE_SET = 0x20;
    public static final int COSPLAY = 0x40;
    public static final int ASIAN_PORN = 0x80;
    public static final int NON_H = 0x100;
    public static final int WESTERN = 0x200;
    public static final int ALL_CATEGORY = 0x3ff;

    @Override
    public EhConfig clone() {
        try {
            return (EhConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
