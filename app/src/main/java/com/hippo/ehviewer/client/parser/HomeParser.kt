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

import com.hippo.ehviewer.client.exception.InsufficientFundsException
import com.hippo.ehviewer.client.exception.ParseException
import org.jsoup.Jsoup

object HomeParser {
    private val PATTERN_FUNDS =
        Regex("Available: ([\\d,]+) Credits.*Available: ([\\d,]+) kGP", RegexOption.DOT_MATCHES_ALL)
    private const val INSUFFICIENT_FUNDS = "Insufficient funds."

    fun parse(body: String): Limits {
        Jsoup.parse(body).selectFirst("div.homebox")?.let {
            val es = it.select("p > strong")
            if (es.size == 3) {
                val current = ParserUtils.parseInt(es[0].text(), 0)
                val maximum = ParserUtils.parseInt(es[1].text(), 0)
                val resetCost = ParserUtils.parseInt(es[2].text(), 0)
                return Limits(current, maximum, resetCost)
            }
        }
        throw ParseException("Parse image limits error")
    }

    fun parseResetLimits(body: String): Limits {
        if (body.contains(INSUFFICIENT_FUNDS)) {
            throw InsufficientFundsException()
        }
        return parse(body)
    }

    fun parseFunds(body: String): Funds {
        PATTERN_FUNDS.find(body)?.groupValues?.run {
            val fundsC = ParserUtils.parseInt(get(1), 0)
            val fundsGP = ParserUtils.parseInt(get(2), 0) * 1000
            return Funds(fundsGP, fundsC)
        }
        throw ParseException("Parse funds error")
    }

    data class Limits(val current: Int = 0, val maximum: Int, val resetCost: Int = 0)
    data class Funds(val fundsGP: Int, val fundsC: Int)
    class Result(val limits: Limits, val funds: Funds)
}
