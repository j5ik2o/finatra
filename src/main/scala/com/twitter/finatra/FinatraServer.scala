/**
 * Copyright (C) 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.finatra

import com.twitter.finagle.builder.{Server, ServerBuilder}


import com.twitter.finagle.http._
import com.twitter.finagle.http.{Request => FinagleRequest, Response => FinagleResponse}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.logging.config._
import com.twitter.logging.{FileHandler, LoggerFactory, Logger}
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import com.twitter.finagle.tracing.{Tracer, NullTracer}
import com.twitter.conversions.storage._
import com.twitter.util.StorageUnit
import com.twitter.ostrich.admin._
import com.twitter.ostrich.admin.{Service => OstrichService}

object FinatraServer {

  var fs:FinatraServer = new FinatraServer

  def register(app: Controller) {
    fs.register(app)
  }

  def start() {
    fs.start()
  }

  def addFilter(filter: SimpleFilter[FinagleRequest, FinagleResponse]) {
    fs.addFilter(filter)
  }

}

class FinatraServer extends Logging with OstrichService {

  val controllers = new ControllerCollection
  var filters:Seq[SimpleFilter[FinagleRequest, FinagleResponse]] = Seq.empty

  val pid = ManagementFactory.getRuntimeMXBean().getName().split('@').head

  def allFilters(baseService: Service[FinagleRequest, FinagleResponse]) = {
    filters.foldRight(baseService) { (b,a) => b andThen a }
  }

  def register(app: Controller) { controllers.add(app) }

  def addFilter(filter: SimpleFilter[FinagleRequest, FinagleResponse]) { filters = filters ++ Seq(filter) }

  def initLogger() {

    val handler = FileHandler(
        filename = "log/finatra.log",
        rollPolicy = Policy.Never,
        append = false,
        level = Some(Level.INFO))

    val log: Logger = LoggerFactory(
      node = "com.twitter",
      level = Some(Level.DEBUG),
      handlers = List(handler)).apply()

  }

  def initAdminService(runtimeEnv: RuntimeEnvironment) {
      AdminServiceFactory(
        httpPort = Config.getInt("stats_port"),
        statsNodes = StatsFactory(
          reporters = JsonStatsLoggerFactory(serviceName = Some("finatra")) ::
                  TimeSeriesCollectorFactory() :: Nil
        ) :: Nil
      )(runtimeEnv)
  }


  def shutdown() {
    logger.info("shutting down")
    println("finatra process shutting down")
    System.exit(0)
  }

  def start() {
    start(NullTracer, new RuntimeEnvironment(this))
  }

  def start(tracer: Tracer = NullTracer, runtimeEnv: RuntimeEnvironment = new RuntimeEnvironment(this)) {

    ServiceTracker.register(this)

    if(Config.getBool("stats_enabled")){
      initAdminService(runtimeEnv)
    }

    initLogger()

    val appService = new AppService(controllers)
    val fileService = new FileService

    addFilter(fileService)

    val port = Config.getInt("port")

    val service: Service[FinagleRequest, FinagleResponse] = allFilters(appService)

    val http = Http().maxRequestSize(Config.getInt("max_request_megabytes").megabyte)

    val codec = new RichHttp[FinagleRequest](http)

    val server: Server = ServerBuilder()
      .codec(codec)
      .bindTo(new InetSocketAddress(port))
      .tracer(tracer)
      .name(Config.get("name"))
      .build(service)

    logger.info("process %s started on %s", pid, port)

    println("finatra process " + pid + " started on port: " + port.toString)
    println("config args:")
    Config.printConfig()

  }
}


