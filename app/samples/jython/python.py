"""
"""
import json

body = json.loads(req.body())
_res = "Hello :" + body["name"] + "!"

