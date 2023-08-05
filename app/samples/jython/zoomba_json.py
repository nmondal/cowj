"""
Uses ZoomBA Library to show how JSON can be used
ttps://www.tutorialspoint.com/jython/jython_importing_java_libraries.htm
"""
from zoomba.lang.core.types import ZTypes

body = ZTypes.json(req.body())
_res = "Hello :" + body["name"] + "!"

