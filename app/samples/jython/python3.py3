"""
Graal Python, Python 3
"""
import json

body = json.loads(req.body())
"Hello :" + body["name"] + "!" # just return

