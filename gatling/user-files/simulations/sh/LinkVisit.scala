package sh

import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import io.gatling.http.Predef._

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.Random

case class Link(val longURL: String, val link: String, val counter: AtomicLong) {
  def getAndCount(): String = {
    counter.incrementAndGet()
    return link
  }
}

object Runner {
  val localHostname = java.net.InetAddress.getLocalHost().getHostName()
}

object CustomFeeders {
  val MaxNumberOfLink = Integer.getInteger("links.max", 4)
  val count = new AtomicLong()
  val maxLink = new AtomicLong(MaxNumberOfLink.longValue())

  val shortenLink = new ConcurrentHashMap[String, AtomicLong]()
  val links = new ArrayBuffer[Link]()

  def uniq(): Feeder[String] = {
    return Iterator.continually(
      Map(
        ("uniq" -> s"${Random.alphanumeric.take(10).mkString}-${count.getAndIncrement()}")
      )
    )
  }

  def addLink(link: String, longURL: String): Unit = {
    if (maxLink.getAndDecrement() <= 0) {
      return
    }

    val c = new AtomicLong()
    val oc = shortenLink.putIfAbsent(link, c)
    if (oc == null) {
      links.synchronized {
        links.addOne(Link(longURL, link, c))
      }
    }
  }
}

class RandomLinksFeeder extends Feeder[Link] {
  override def hasNext: scala.Boolean = true

  override def next(): Map[String, Link] = {
    val links = CustomFeeders.links

    while (links.isEmpty) {
      Thread.sleep(100)
    }

    links.synchronized {
      Map(
        "data" -> links(Random.nextInt(links.length))
      )
    }
  }
}

class LinksFeeder extends Feeder[Option[Link]] {
  override def hasNext: scala.Boolean = true

  override def next(): Map[String, Option[Link]] = {
    val links = CustomFeeders.links
    links.synchronized {
      if (links.isEmpty) {
        Map(
          "data" -> Option.empty[Link]
        )
      } else {
        Map(
          "data" -> Option(links.remove(0))
        )
      }
    }
  }
}


class ShortenSimulation extends Simulation {
  val host = System.getProperty("hostname", "ta.tnpl.me:9000")

  val httpProto = http.baseUrl(s"http://${host}")
    .contentTypeHeader("application/json")
    .disableFollowRedirect
    .disableCaching

  val uniqString = CustomFeeders.uniq()

  val shortenScene = scenario("shorten link")
    .feed(uniqString)
    .exec { session =>
      session.set("longURL", s"http://www.google.com?q=${session("uniq").as[String]}")
    }
    .exec(
      http(s"shorten - ${Runner.localHostname}")
        .post("/link")
        .body(StringBody(
          """
            |{
            | "url": "${longURL}"
            |}
            |""".stripMargin))
        .check(status.is(200))
        .check(jsonPath("$.link").ofType[String].saveAs("link"))
    )
    .exitHereIfFailed
    .exec { session =>
      val link = session("link").as[String]
      val longURL = session("longURL").as[String]
      CustomFeeders.addLink(link, longURL)

      session
    }

  val visitScene = scenario("visit link")
    .repeat(10) {
      feed(new RandomLinksFeeder())
        .exec { session =>
          val l = session("data").as[Link]
          session.set("link", l.getAndCount()) //session immutable
            .set("longURL", l.longURL)
        }
        .exec { session =>
          //      println("Setting visit link data:" + session)
          session
        }
        .exec(
          http(s"visit - ${Runner.localHostname}")
            .get("${link}")
            .disableFollowRedirect
            .check(status.is(302))
            .check(header("Location").is("${longURL}"))
        )
    }

  val validateScene = scenario("validate stats")
    .doWhile("${available}") {
      feed(new LinksFeeder())
        .exec { session =>
          val data = session("data").as[Option[Link]]
          if (data.isEmpty) {
            session.set("available", false)
          } else {
            val l = data.get
            session.set("available", true)
              .set("count", l.counter.get())
              .set("statsURL", l.link + "/stats")
          }
        }
        .exec(
          http(s"stats - ${Runner.localHostname}")
            .get("${statsURL}")
            .check(status.is(200))
            .check(jsonPath("$.visit").ofType[Long].is("${count}"))
        )
    }

  setUp(
    shortenScene.inject(
      constantConcurrentUsers(Integer.getInteger("load.shorten", 2)) during (Integer.getInteger("load.shorten.duration", 10) seconds)
    ),
    visitScene.inject(
      constantConcurrentUsers(Integer.getInteger("load.visit", 5)) during (Integer.getInteger("load.visit.duration", 10) seconds)
    ).andThen(
      validateScene.inject(
        atOnceUsers(Math.min(CustomFeeders.MaxNumberOfLink, 50))
      )
    ),
  )
    .protocols(httpProto)
    .maxDuration(2 minute)
}

