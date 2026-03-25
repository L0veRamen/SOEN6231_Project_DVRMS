package client;

final class WsEndpoint {

  static final String SOURCE_ARGS = "args";
  static final String SOURCE_DEFAULT = "default";

  private final String wsdlUrl;
  private final String source;

  WsEndpoint(String wsdlUrl, String source) {
    this.wsdlUrl = wsdlUrl;
    this.source = source;
  }

  String wsdlUrl() {
    return wsdlUrl;
  }

  String source() {
    return source;
  }
}
