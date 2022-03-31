package webpos

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class Test extends Simulation {
  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader(
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0");

  val scn = scenario("WebPos JD Test")
    .exec(http("request").get("/"))
    .exec(http("request").get("/add?pid=13284888"))

  setUp(scn.inject(atOnceUsers(500)).protocols(httpProtocol))
}
