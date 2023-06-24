package cowj.plugins;

import cowj.DataSource;
import cowj.EitherMonad;
import cowj.Model;
import org.junit.Assert;
import org.junit.Test;
import zoomba.lang.core.io.ZWeb;

import java.util.Collections;
import java.util.Map;

public class CurlWrapperTest {

    public Model model = () -> ".";

    @Test
    public void curlErrorTest(){
        DataSource dataSource = CurlWrapper.CURL.create( "foo", Map.of(), model);
        Assert.assertEquals( "foo", dataSource.name());
        Object cw = dataSource.proxy();
        Assert.assertTrue( cw instanceof  CurlWrapper );
        EitherMonad<ZWeb.ZWebCom> res = ((CurlWrapper) cw).send("get", "/", Collections.emptyMap(), Collections.emptyMap(), "");
        Assert.assertTrue( res.inError() );
        Assert.assertTrue( res.error().getMessage().contains("no protocol") );
    }
}
