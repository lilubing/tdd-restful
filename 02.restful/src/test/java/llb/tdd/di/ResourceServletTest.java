package llb.tdd.di;

import jakarta.servlet.Servlet;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Providers;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author LiLuBing
 * @PackageName: llb.tdd.di
 * @Description:
 * @ClassName: ResourceServletTest
 * @date 2022-11-09 7:52:03
 * @ProjectName tdd
 * @Version V1.0
 */
public class ResourceServletTest extends ServletTest {
	private Runtime runtime;
	private ResourceRouter router;
	private ResourceContext resourceContext;
	private Providers providers;
	private OutBoundResponseBuilder response;


	@Override
	protected Servlet getServlet() {
		runtime = Mockito.mock(Runtime.class);
		router = Mockito.mock(ResourceRouter.class);
		resourceContext = Mockito.mock(ResourceContext.class);
		providers = Mockito.mock(Providers.class);

		when(runtime.getResourceRouter()).thenReturn(router);
		when(runtime.createResourceContext(any(), any())).thenReturn(resourceContext);
		when(runtime.getProviders()).thenReturn(providers);

		return new ResourceServlet(runtime);
	}

	@BeforeEach
	public void before() {
		response = new OutBoundResponseBuilder();

		RuntimeDelegate delegate = Mockito.mock(RuntimeDelegate.class);RuntimeDelegate.setInstance(delegate);
		when(delegate.createHeaderDelegate(eq(NewCookie.class))).thenReturn(new RuntimeDelegate.HeaderDelegate<NewCookie>() {
			@Override
			public NewCookie fromString(String value) {
				return null;
			}

			@Override
			public String toString(NewCookie value) {
				return value.getName() + "=" + value.getValue();
			}
		});

	}

	// TODO: use status code as http status
	@Test
	public void should_use_status_from_response() throws Exception {
		response.status(Response.Status.NOT_MODIFIED).returnFrom(router);

		HttpResponse<String> httpResponse = get(" /test");

		assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), httpResponse.statusCode());
	}
	// TODO: use headers as http headers
	@Test
	public void should_use_http_headers_from_response() throws Exception {
		response.headers("Set-Cookie", new NewCookie.Builder("SESSION_ID").value("session").build(),
				new NewCookie.Builder("USER_ID").value("user").build()).returnFrom(router);

		HttpResponse<String> httpResponse = get("/test");

		assertArrayEquals(new String[]{"SESSION_ID=session", "USER_ID=user" }, httpResponse.headers().allValues("Set-Cookie").toArray(String[]::new));
	}

	// TODO: writer body using MessageBodyWriter
	@Test
	public void should_write_entity_to_http_response_using_message_body_writer() throws Exception {
		response.entity(new GenericEntity<>("entity", String.class), new Annotation[0]).returnFrom(router);

		HttpResponse<String> httpResponse = get("/test");
		assertEquals("entity", httpResponse.body());
	}
	@Test
	public void should_use_response_from_web_app_ex() throws Exception {
		response.status(Response.Status.FORBIDDEN)
				.entity(new GenericEntity<>("error", String.class), new Annotation[0])
				.headers(HttpHeaders.SET_COOKIE, new NewCookie.Builder("SESSION_ID").value("session").build())
				.throwFrom(router);

		HttpResponse<String> httpResponse = get("/test");

		assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
		assertArrayEquals(new String[]{"SESSION_ID=session"}, httpResponse.headers().allValues(HttpHeaders.SET_COOKIE).toArray(String[]::new));
		assertEquals("error", httpResponse.body());
//		assertArrayEquals(new String[]{"SESSION_ID=session", "USER_ID=user"},
//				httpResponse.headers().allValues("Set-Cookie").toArray(String[]::new));
	}
	private static MultivaluedMap<String, Object> testHeader() {
		MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
		headers.addAll("Set-Cookie",
				new NewCookie.Builder("SESSION_ID").value("session").build(),
				new NewCookie.Builder("USER_ID").value("user").build());
		return headers;
	}
	// TODO: throw other exception,use ExceptionMapper build response
	@Test
	public void should_build_response_by_exception_mapper_if_null_response_from_web_application_exception() throws Exception {
		when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
		when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception -> response.status(Response.Status.FORBIDDEN).build());
		HttpResponse<String> httpResponse = get("/test");

		assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
	}

	@Test
	public void should_not_call_message_body_writer_if_entity_is_null() throws Exception {
		response.entity(null, new Annotation[0]).returnFrom(router);

		HttpResponse<String> httpResponse = get("/test");
		assertEquals(Response.Status.OK.getStatusCode(), httpResponse.statusCode());
		assertEquals("", httpResponse.body());
	}

	// TODO: 500 if MessageBodyWriter not found
	// TODO: 500 if header delegate∂
	// TODO: 500 if exception mapper

	// TODO exception mapper
	@Test
	public void should_use_response_from_web_application_exception_thrown_by_exception_mapper() throws Exception {
		when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
		when(providers.getExceptionMapper(eq(RuntimeException.class)))
				.thenReturn(exception -> {
					throw new WebApplicationException(response.status(Response.Status.FORBIDDEN).build());
				});
		HttpResponse<String> httpResponse = get("/test");

		assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
	}

	@Test
	public void should_map_exception_thrown_by_exception_mapper() throws Exception {
		when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
		when(providers.getExceptionMapper(eq(RuntimeException.class)))
				.thenReturn(exception -> {
					throw new IllegalArgumentException();
				});

		when(providers.getExceptionMapper(eq(IllegalArgumentException.class)))
				.thenReturn(exception -> response.status(Response.Status.FORBIDDEN).build());

		HttpResponse<String> httpResponse = get("/test");

		assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
	}
	// TODO providers gets exception mapper
	// TODO runtime delegate
	// TODO header delegate
	// TODO providers gets message body writer
	// TODO message body writer write

	class OutBoundResponseBuilder {
		Response.Status status = Response.Status.OK;
		MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
		GenericEntity<String> entity = new GenericEntity<>("entity", String.class);
		Annotation[] annotations = new Annotation[0];
		MediaType mediaType = MediaType.TEXT_PLAIN_TYPE;

		public OutBoundResponseBuilder status(Response.Status status) {
			this.status = status;
			return this;
		}

		public OutBoundResponseBuilder headers(String name, Object... values) {
			headers.addAll(name, values);
			return this;
		}

		public OutBoundResponseBuilder entity(GenericEntity<String> entity, Annotation[] annotations) {
			this.entity = entity;
			this.annotations = annotations;
			return this;
		}

		void returnFrom(ResourceRouter router) {
			returnFrom(response -> when(router.dispatch(any(), eq(resourceContext))).thenReturn(response));
		}

		void throwFrom(ResourceRouter router) {
			returnFrom(response -> {
				WebApplicationException exception = new WebApplicationException(response);
				when(router.dispatch(any(), eq(resourceContext))).thenThrow(exception);
			});
		}

		void returnFrom(Consumer<OutboundResponse> consumer) {
			OutboundResponse response = build();

			consumer.accept(response);

		}

		OutboundResponse build() {
			OutboundResponse response = Mockito.mock(OutboundResponse.class);
			when(response.getStatus()).thenReturn(status.getStatusCode());
			when(response.getStatusInfo()).thenReturn(status);
			when(response.getHeaders()).thenReturn(headers);
			when(response.getGenericEntity()).thenReturn(entity);
			when(response.getAnnotations()).thenReturn(annotations);
			when(response.getMediaType()).thenReturn(mediaType);

			stubMessageBodyWriter();

			return response;
		}

		private void stubMessageBodyWriter() {
			when(providers.getMessageBodyWriter(eq(String.class), eq(String.class), same(annotations), eq(mediaType)))
					.thenReturn(new MessageBodyWriter<String>() {
						@Override
						public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
							return false;
						}

						@Override
						public void writeTo(String s, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
							PrintWriter writer = new PrintWriter(entityStream);
							writer.write(s);
							writer.flush();
						}
					});
		}
	}
}
