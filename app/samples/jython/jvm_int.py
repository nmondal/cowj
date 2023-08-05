from zoomba.lang.core.types import ZTypes

body = ZTypes.json(req.body())
_res = "Hello :" + body["name"] + "!"
