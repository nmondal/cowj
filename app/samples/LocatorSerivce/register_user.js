print( req )
print( resp )

redis = _ds.get('redis')
redis.set('mykey', 'thevalueofmykey')
_res = redis.get('mykey')
input = req.attribute("_body")
input.first_name