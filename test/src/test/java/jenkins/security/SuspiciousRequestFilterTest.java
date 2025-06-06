package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URL;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.GET;

@Issue("SECURITY-1774")
@WithJenkins
class SuspiciousRequestFilterTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private WebResponse get(String path) throws Exception {
        return j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(new WebRequest(new URL(j.getURL(), path)))
                .getWebResponse();
    }

    @Test
    void denySemicolonInRequestPathByDefault() throws Exception {
        WebResponse response = get("foo/bar/..;/?baz=bruh");
        assertThat(Foo.getInstance().baz, is(nullValue()));
        assertThat(response.getStatusCode(), is(HttpServletResponse.SC_BAD_REQUEST));
        // Actually served by Jetty; never even gets as far as SuspiciousRequestFilter.
        assertThat(response.getContentAsString(), containsString("path parameter"));
    }

    @Disabled("No longer passes Jetty")
    @Test
    void allowSemicolonsInRequestPathWhenEscapeHatchEnabled() throws Exception {
        SuspiciousRequestFilter.allowSemicolonsInPath = true;
        try {
            WebResponse response = get("foo/bar/..;/..;/cli?baz=bruh");
            assertThat(Foo.getInstance().baz, is("bruh"));
            assertThat(response.getStatusCode(), is(HttpServletResponse.SC_OK));
        } finally {
            SuspiciousRequestFilter.allowSemicolonsInPath = false;
        }
    }

    @Test
    void allowSemicolonsInQueryParameters() throws Exception {
        WebResponse response = get("foo/bar?baz=foo;bar=baz");
        assertThat(Foo.getInstance().baz, is("foo;bar=baz"));
        assertThat(response.getStatusCode(), is(HttpServletResponse.SC_OK));
    }

    @TestExtension
    public static class Foo implements UnprotectedRootAction {

        private static Foo getInstance() {
            return ExtensionList.lookupSingleton(Foo.class);
        }

        private String baz;

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return "Pitied Foos";
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return "foo";
        }

        @GET
        public void doBar(@QueryParameter String baz) {
            this.baz = baz;
        }

        @GET
        public void doIndex(@QueryParameter String baz) {
            this.baz = "index: " + baz;
        }
    }

}
