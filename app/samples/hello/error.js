// https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/418
// these should not fail, at all
Test.panic(false)
Test.panic(false, "This should not fail!" )
Test.panic(false, "This should not fail!", 418 )

Test.expect(true)
Test.expect(true, "This should not fail!" )
Test.expect(true, "This should not fail!", 418 )
// showing custom errors
Test.expect(false, "boom!", 418 )

