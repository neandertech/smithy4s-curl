$version: "2.0"

namespace httpbin

use alloy#simpleRestJson
use alloy#uuidFormat

@simpleRestJson
service HttpBinService {
  version: "1.0.0",
  operations: [GetIP, Anything]
}

@readonly
@http(method: "GET", uri: "/ip", code: 200)
operation GetIP {
  output := {
    origin: String 
  }
}

@http(method: "POST", uri: "/anything", code: 200)
operation Anything {
  input := {
    test: String,

    @required
    id: Integer,

    @httpQuery("my-q")
    queryParam: String
  }
  output := {
    origin: String,
    method: String,
    data: String,
    url: String
  }
}


map HeaderMap {
    key: String
    value: String
}
