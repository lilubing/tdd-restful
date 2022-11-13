package llb.tdd.di;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author LiLuBing
 * @PackageName: llb.tdd.di
 * @Description:
 * @ClassName: ResourceRouter
 * @date 2022-11-09 7:35:58
 * @ProjectName tdd
 * @Version V1.0
 */
interface ResourceRouter {
	OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext);

	interface Resource {
		Optional<ResourceMethod> match(UriTemplate.MatchResult result, String method, String[] mediaType, UriInfoBuilder builder);
	}

	interface RootResource extends Resource {
		UriTemplate getUriTemplate();
	}

	interface ResourceMethod {
		default String getHttpMethod() {return "";}

		default UriTemplate getUriTemplate() {return null;}

		GenericEntity<?> call(ResourceContext resourceContext, UriInfoBuilder builder);
	}
}

class DefaultResourceRouter implements ResourceRouter {
	private Runtime runtime;
	private List<RootResource> rootResources;
	public DefaultResourceRouter(Runtime runtime, List<RootResource> rootResources) {
		this.runtime = runtime;
		this.rootResources = rootResources;
	}
	@Override
	public OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext) {
		String path = request.getServletPath();
		UriInfoBuilder uri = runtime.createUriInfoBuilder(request);

		Optional<ResourceMethod> method = rootResources.stream().map(resource -> match(path, resource))
				.filter(Result::isMatched).sorted().findFirst().flatMap(result -> result.findResourceMethod(request, uri));

		if (method.isEmpty()) {
			return (OutboundResponse) Response.status(Response.Status.NOT_FOUND).build();
		}
		return (OutboundResponse) method.map(m -> m.call(resourceContext, uri)).map(entity -> Response.ok().entity(entity).build())
				.orElseGet(() -> Response.status(Response.Status.NO_CONTENT).build());
	}

	private Result match(String path, RootResource resource) {
		return new Result(resource.getUriTemplate().match(path), resource);
	}

	record Result(Optional<UriTemplate.MatchResult> matched, RootResource resource) implements Comparable<Result> {
		private boolean isMatched() {
			return matched.isPresent();
		}

		@Override
		public int compareTo(Result o) {
			return matched.flatMap(x -> o.matched.map(x::compareTo)).orElse(0);
		}

		private Optional<ResourceMethod> findResourceMethod(HttpServletRequest request, UriInfoBuilder uri) {
			return matched.flatMap(result -> resource.match(result, request.getMethod(),
					Collections.list(request.getHeaders(HttpHeaders.ACCEPT)).toArray(String[]::new), uri));
		}
	}
}

class ResourceMethods {
	private Map<String, List<ResourceRouter.ResourceMethod>> resourceMethods;
	public ResourceMethods(Method[] methods) {
		this.resourceMethods = getResourceMethods(methods);
	}

	private static Map<String, List<ResourceRouter.ResourceMethod>> getResourceMethods(Method[] methods) {
		return Arrays.stream(methods).filter(m -> Arrays.stream(m.getAnnotations())
						.anyMatch(a -> a.annotationType().isAnnotationPresent(HttpMethod.class)))
				.map(DefaultResourceMethod::new)
				.collect(Collectors.groupingBy(ResourceRouter.ResourceMethod::getHttpMethod));
	}

	public Optional<ResourceRouter.ResourceMethod> findResourceMethods(String path, String method) {
		return Optional.ofNullable(resourceMethods.get(method)).flatMap(methods -> methods.stream().map(m -> ResourceMethods.match(path, m)).filter(ResourceMethods.Result::isMatched).sorted()
				.findFirst().map(ResourceMethods.Result::resourceMethod));
	}

	private static Result match(String path, ResourceRouter.ResourceMethod method) {
		return new Result(method.getUriTemplate().match(path), method);
	}

	static record Result(Optional<UriTemplate.MatchResult> matched,
						 ResourceRouter.ResourceMethod resourceMethod) implements Comparable<Result> {
		public boolean isMatched() {
			return matched.map(r -> r.getRemaining() == null).orElse(false);
		}
		@Override
		public int compareTo(Result o) {
			return matched.flatMap(x -> o.matched.map(x::compareTo)).orElse(0);
		}
	}
}

class RootResourceClass implements ResourceRouter.RootResource {
	private PathTemplate uriTemplate;
	private Class<?> resourceClass;
	private ResourceMethods resourceMethods;

	public RootResourceClass(Class<?> resourceClass) {
		this.resourceClass = resourceClass;
		this.uriTemplate = new PathTemplate(resourceClass.getAnnotation(Path.class).value());
		this.resourceMethods = new ResourceMethods(resourceClass.getMethods());
	}

	@Override
	public Optional<ResourceRouter.ResourceMethod> match(UriTemplate.MatchResult result, String method, String[] mediaTypes, UriInfoBuilder builder) {
		String remaining = Optional.ofNullable(result.getRemaining()).orElse("");
		return resourceMethods.findResourceMethods(remaining, method);
	}

	@Override
	public UriTemplate getUriTemplate() {
		return uriTemplate;
	}
}

class DefaultResourceMethod implements ResourceRouter.ResourceMethod {
	private String httpMethod;
	private UriTemplate uriTemplate;
	private Method method;
	public DefaultResourceMethod(Method method) {
		this.method = method;
		this.uriTemplate = new PathTemplate(Optional.ofNullable(method.getAnnotation(Path.class)).map(Path::value).orElse(""));
		this.httpMethod = Arrays.stream(method.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(HttpMethod.class))
				.findFirst().get().annotationType().getAnnotation(HttpMethod.class).value();
	}
	@Override
	public String getHttpMethod() {
		return httpMethod;
	}
	@Override
	public UriTemplate getUriTemplate() {
		return uriTemplate;
	}
	@Override
	public GenericEntity<?> call(ResourceContext resourceContext, UriInfoBuilder builder) {
		return null;
	}
	@Override
	public String toString() {
		return method.getDeclaringClass().getSimpleName() + "." + method.getName();
	}
}

class SubResource implements ResourceRouter.Resource {
	private Object subResource;
	private ResourceMethods resourceMethods;

	public SubResource(Object subResource) {
		this.subResource = subResource;
		this.resourceMethods = new ResourceMethods(subResource.getClass().getMethods());
	}

	@Override
	public Optional<ResourceRouter.ResourceMethod> match(UriTemplate.MatchResult result, String method, String[] mediaType, UriInfoBuilder builder) {
		String remaining = Optional.ofNullable(result.getRemaining()).orElse("");
		return resourceMethods.findResourceMethods(remaining, method);
	}
}