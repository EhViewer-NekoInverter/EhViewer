/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.ehviewer.client.parser

import com.hippo.ehviewer.client.exception.ParseException

object HomeParser {
    private val PATTERN_FUNDS =
        Regex("Available: ([\\d,]+) Credits.*Available: ([\\d,]+) kGP", RegexOption.DOT_MATCHES_ALL)

    fun parseFunds(body: String): Funds {
        PATTERN_FUNDS.find(body)?.groupValues?.run {
            val fundsC = ParserUtils.parseInt(get(1), 0)
            val fundsGP = ParserUtils.parseInt(get(2), 0) * 1000
            return Funds(fundsGP, fundsC)
        }
        throw ParseException("Parse funds error", body)
    }

    data class Funds(val fundsGP: Int, val fundsC: Int)
}