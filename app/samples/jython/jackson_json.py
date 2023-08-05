"""
Uses Jackson Library to show how JSON can be used
https://www.tutorialspoint.com/jython/jython_importing_java_libraries.htm
Granted you will not have normal python but you can have Java Equivalent of Python
"""
from com.fasterxml.jackson.databind import ObjectMapper
from java.util import Map

mapper = ObjectMapper()
body = mapper.readValue( req.body(), Map)
_res = "Hello :" + body["name"] + "!"
