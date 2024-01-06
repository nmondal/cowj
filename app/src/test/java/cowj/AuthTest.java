package cowj;

import org.junit.*;
import zoomba.lang.core.io.ZWeb;

import java.util.Collections;

public class AuthTest {
    static ModelRunner mr;
    @BeforeClass
    public static void before(){
        mr = ModelRunnerTest.runModel("samples/auth_demo/auth_demo.yaml" );
    }
    @AfterClass
    public static void after(){
        if ( mr == null ) return;
        mr.stop();
        mr = null;
    }

    static EitherMonad<ZWeb.ZWebCom> get(String base, String path , String userName){
        ZWeb zWeb = new ZWeb(base);
        try {
            if ( userName != null ){
                zWeb.headers.put("u", userName);
            }
            ZWeb.ZWebCom r = zWeb.get(path, Collections.emptyMap());
            return  EitherMonad.value(r);
        }catch (Exception ex){
            return EitherMonad.error(ex);
        }
    }

    static EitherMonad<ZWeb.ZWebCom> post( String base, String path, String userName ){
        ZWeb zWeb = new ZWeb(base);
        try {
            if ( userName != null ){
                zWeb.headers.put("u", userName);
            }
            ZWeb.ZWebCom r = zWeb.post(path, Collections.emptyMap(), "");
            return  EitherMonad.value(r);
        }catch (Exception ex){
            return EitherMonad.error(ex);
        }
    }

    @Test
    public void loadAuthTest(){
        AuthSystem authSystem = AuthSystem.fromFile( "samples/auth_demo/auth/auth.yaml", mr.model());
        Assert.assertFalse( authSystem.disabled() );
        Assert.assertFalse( authSystem.policy().isEmpty());
    }

    @Test
    public void loadWrongFile(){
        AuthSystem as = AuthSystem.fromFile( "samples/auth_demo/auth/auth111.yaml", mr.model() );
        Assert.assertEquals( AuthSystem.NULL, as);
        Assert.assertTrue( as.policy().isEmpty());
    }

    @Test
    public void guestAccessTest(){
        // fail get entity access
        EitherMonad<ZWeb.ZWebCom> em = get("http://localhost:6042", "/entity/1111", null);
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 401, em.value().status );
        // should be able to do static access...
        em = get("http://localhost:6042", "/hello.json", null);
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 200, em.value().status );
    }

    @Test
    public void nonMemberAccessTest(){
        //  no get entity access
        EitherMonad<ZWeb.ZWebCom> em = get("http://localhost:6042", "/entity/1111", "foo");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 403, em.value().status );
        // should be able to do static access...
        em = get("http://localhost:6042", "/hello.json", "foo");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 200, em.value().status );
        // should not be able to do post access
        em = post("http://localhost:6042", "/entity", "foo");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 403, em.value().status );
    }

    @Test
    public void memberAccessTest(){
        // ok get entity access
        EitherMonad<ZWeb.ZWebCom> em = get("http://localhost:6042", "/entity/1111", "bob");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 200, em.value().status );
        // should be able to do static access...
        em = get("http://localhost:6042", "/hello.json", "bob");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 200, em.value().status );
        // should not be able to do post access
        em = post("http://localhost:6042", "/entity", "bob");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 403, em.value().status );
    }

    @Test
    public void adminAccessTest(){
        // ok get entity access
        EitherMonad<ZWeb.ZWebCom> em = get("http://localhost:6042", "/entity/1111", "alice");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 200, em.value().status );
        // should be able to do static access...
        em = get("http://localhost:6042", "/hello.json", "bob");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 200, em.value().status );
        // should not be able to do post access
        em = post("http://localhost:6042", "/entity", "alice");
        Assert.assertFalse( em.inError() );
        Assert.assertEquals( 200, em.value().status );
    }
}
