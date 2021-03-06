/*******************************************************************************
 * Copyright (c) 2010 Oobium, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Jeremy Dowdall <jeremy@oobium.com> - initial API and implementation
 ******************************************************************************/
package org.oobium.app.routing;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.AUTHORIZATION;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.oobium.app.http.Action.create;
import static org.oobium.app.http.Action.destroy;
import static org.oobium.app.http.Action.show;
import static org.oobium.app.http.Action.showAll;
import static org.oobium.app.http.Action.showEdit;
import static org.oobium.app.http.Action.showNew;
import static org.oobium.app.http.Action.update;
import static org.oobium.utils.StringUtils.asString;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.oobium.app.AppService;
import org.oobium.app.controllers.HttpController;
import org.oobium.app.http.Action;
import org.oobium.app.request.Request;
import org.oobium.app.routing.handlers.AssetHandler;
import org.oobium.app.routing.handlers.AuthorizationHandler;
import org.oobium.app.routing.handlers.StyleSheetHandler;
import org.oobium.app.routing.handlers.HttpHandler;
import org.oobium.app.routing.handlers.RedirectHandler;
import org.oobium.app.routing.handlers.ViewHandler;
import org.oobium.app.server.ServerConfig;
import org.oobium.app.views.ScriptFile;
import org.oobium.app.views.StyleSheet;
import org.oobium.app.views.View;
import org.oobium.logging.Logger;
import org.oobium.persist.Model;
import org.oobium.persist.ModelDescription;
import org.oobium.persist.Relation;
import org.oobium.utils.Base64;

public class RouterTests {

	@ModelDescription(hasMany={@Relation(name="categories",type=Category.class,opposite="cAccount")})
	public static class Account extends Model {
		public Account() { setId(0); set("type", "checking"); set("name", "personal"); }
	}
	public static class AccountController extends HttpController { }

	@ModelDescription(hasOne={@Relation(name="cAccount",type=Account.class,opposite="categories")})
	public static class Category extends Model {
		public Category() { setId(0); set("cAccount", "0"); }
	}
	public static class CategoryController extends HttpController { }

	public static class AccountView extends View { }
	public static class MyView extends View { }
	public static class InvalidView extends View { InvalidView(String str) { } }

	
	@ModelDescription(hasMany={@Relation(name="phones",type=Phone.class)})
	public static class Member extends Model {
		public Member() { setId(0); set("type", "admin"); }
	}
	public static class MemberController extends HttpController { }

	@ModelDescription(hasOne={@Relation(name="member",type=Member.class)})
	public static class Phone extends Model { }
	public static class PhoneController extends HttpController { }

	public static class AccountStyles extends StyleSheet { }
	public static class AccountScripts extends ScriptFile {
		protected void doRender(StringBuilder sb) throws Exception { /*do nothing*/ }
		public boolean hasSource() { return false; }
	}
	
	
	private AppService service;
	private Logger logger;
	private AppRouter router;
	private Account account;
	private Category category;

	private Request request(String desc) {
		String[] sa = desc.split(" +");
		String method = sa[0].substring(1, sa[0].length()-1);
		String fullPath = sa[1];
		sa = sa[1].split("\\?");
		String path = sa[0];
		boolean hasParams = sa.length == 2;
		
		String host = service.getServerConfig().host();
		int port = service.getServerConfig().port();
		
		Request request = mock(Request.class);
		when(request.getHandler()).thenReturn(service);
		when(request.getHost()).thenReturn(host);
		when(request.getPort()).thenReturn(port);
		when(request.getMethod()).thenReturn(HttpMethod.valueOf(method));
		when(request.getPath()).thenReturn(path);
		when(request.getUri()).thenReturn(fullPath);
		when(request.hasParameters()).thenReturn(hasParams);
		
		return request;
	}
	
	@Before
	@SuppressWarnings("unchecked")
	public void setUp() {
		logger = mock(Logger.class);
		service = mock(AppService.class);
		when(service.getLogger()).thenReturn(logger);
		when(service.getServerConfig()).thenReturn(new ServerConfig("localhost", 5555, false));
		when(service.getControllerClass(any(Class.class))).thenAnswer(new Answer<Class<? extends HttpController>>() {
			@Override
			public Class<? extends HttpController> answer(InvocationOnMock invocation) throws Throwable {
				Object arg = invocation.getArguments()[0];
				if(arg == Account.class)	return AccountController.class;
				if(arg == Category.class)	return CategoryController.class;
				if(arg == Member.class)		return MemberController.class;
				if(arg == Phone.class)		return PhoneController.class;
				throw new IllegalArgumentException(arg + " is not mapped to a controller");
			}
		});
		router = new AppRouter(service);
		router.setApi(null);
		router.removeModelNotifier();
		account = new Account();
		category = new Category();
	}
	
	@After
	public void tearDown() {
		router.clear();
		router = null;
		account = null;
		category = null;
	}

	@Test
	public void testSetHome_Model_ShowAll() throws Exception {
		router.setHome(Account.class, showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] / -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals("null", asString(ch.params));
	}

	@Test
	public void testSetHome_Controller_ShowAll() throws Exception {
		router.setHome(AccountController.class, showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] / -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals("null", asString(ch.params));
	}

	@Test
	public void testSetHome_View() throws Exception {
		router.setHome(AccountView.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] / -> AccountView", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /"));
		assertNotNull(handler);
		assertEquals(ViewHandler.class, handler.getClass());
		ViewHandler vh = (ViewHandler) handler;
		assertEquals(AccountView.class, vh.viewClass);
	}

	@Test
	public void testRouteAssets() throws Exception {
		when(service.getName()).thenReturn("TestApp");
		when(service.getAssetList()).thenReturn(Collections.singletonList("/documents/notes.text|0|0"));
		
		router.addAssetRoutes();
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /documents/notes.text -> Asset", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /documents/notes.text"));
		assertNotNull(handler);
		assertEquals(AssetHandler.class, handler.getClass());
	}
	
	@Test
	public void testRouteAssets_Image() throws Exception {
		when(service.getName()).thenReturn("TestApp");
		when(service.getAssetList()).thenReturn(Collections.singletonList("/images/my_pic.png|0|0"));
		
		router.addAssetRoutes();
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_pic.png -> Asset", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_pic.png"));
		assertNotNull(handler);
		assertEquals(AssetHandler.class, handler.getClass());
	}
	
	@Test
	public void testRouteAssets_Script() throws Exception {
		when(service.getName()).thenReturn("TestApp");
		when(service.getAssetList()).thenReturn(Collections.singletonList("/scripts/application.js|0|0"));
		
		router.addAssetRoutes();
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /application.js -> Asset", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /application.js"));
		assertNotNull(handler);
		assertEquals(AssetHandler.class, handler.getClass());
	}
	
	@Test
	public void testRouteAssets_Style() throws Exception {
		when(service.getName()).thenReturn("TestApp");
		when(service.getAssetList()).thenReturn(Collections.singletonList("/styles/application.css|0|0"));
		
		router.addAssetRoutes();
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /application.css -> Asset", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /application.css"));
		assertNotNull(handler);
		assertEquals(AssetHandler.class, handler.getClass());
	}
	
	@Test
	public void testRouteAssets_WithBasicAuth() throws Exception {
		when(service.getName()).thenReturn("TestApp");
		when(service.getAssetList()).thenReturn(Collections.singletonList("/documents/notes.text|0|0|realm1"));
		
		router.addAssetRoutes();
		
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /documents/notes.text -> Asset", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /documents/notes.text"));
		assertNotNull(handler);
		assertEquals(AuthorizationHandler.class, handler.getClass());

		Request request = request("[GET] /documents/notes.text");
		when(request.getHeader(AUTHORIZATION)).thenReturn("Basic " + Base64.encode("user:secret"));

		handler = router.getHandler(request);
		assertNotNull(handler);
		assertEquals(AuthorizationHandler.class, handler.getClass());
		
		router.addBasicAuthorization("realm1", "user", "secret");
		
		handler = router.getHandler(request);
		assertNotNull(handler);
		assertEquals(AssetHandler.class, handler.getClass());
	}
	
	@Test
	public void testPathErrorLogging() throws Exception {
		router.addResource(Category.class, showAll);
		
		int i = 0;

		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));
		
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, create).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, destroy).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, update).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, show).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, showAll).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, showEdit).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, showNew).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo("home").toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, "categories", create).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, "categories", showAll).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));

		assertEquals("/#", router.pathTo(account, "categories", showNew).toString());
		verify(logger, times(i++)).warn(any(Throwable.class));
		verify(logger, never()).warn(anyString(), any(Throwable.class));
	}

	@Test
	public void testAddModelRoute_ShowAll() throws Exception {
		router.addResource(Account.class, Action.showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ShowAll_WithBasicAuth() throws Exception {
		router.addResource(Account.class, Action.showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		router.addBasicAuthentication(GET, "/accounts", "realm1");
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts"));
		assertNotNull(handler);
		assertEquals(AuthorizationHandler.class, handler.getClass());

		Request request = request("[GET] /accounts");
		when(request.getHeader(AUTHORIZATION)).thenReturn("Basic " + Base64.encode("user:secret"));

		handler = router.getHandler(request);
		assertNotNull(handler);
		assertEquals(AuthorizationHandler.class, handler.getClass());
		
		router.addBasicAuthorization("realm1", "user", "secret");
		
		handler = router.getHandler(request);
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Show() throws Exception {
		router.addResource(Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts/0"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertNotNull(ch.params);
		assertEquals("[[id, 0]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ShowEdit() throws Exception {
		router.addResource(Account.class, Action.showEdit);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/0/edit"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showEdit, ch.action);
		assertEquals("[[id, 0]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ShowNew() throws Exception {
		router.addResource(Account.class, Action.showNew);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts/new"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showNew, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Create() throws Exception {
		router.addResource(Account.class, Action.create);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[POST] /accounts -> AccountController#create", router.getRoutes().get(0).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[POST] /accounts"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(create, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Update() throws Exception {
		router.addResource(Account.class, Action.update);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[PUT] /accounts/0"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(update, ch.action);
		assertEquals("[[id, 0]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Destroy() throws Exception {
		router.addResource(Account.class, Action.destroy);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());

		RouteHandler handler = router.getHandler(request("[DELETE] /accounts/0"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(destroy, ch.action);
		assertEquals("[[id, 0]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Fixed() throws Exception {
		router.addResource("my_account", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_account -> AccountController#show", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/my_account", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Fixed_EndingSlash() throws Exception {
		router.addResource("my_account/", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_account -> AccountController#show", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/my_account", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("null", asString(ch.params));
	}

	@Test
	public void testAddModelRoute_DefaultParts() throws Exception {
		router.addResource("{models}/{id}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts/0"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[id, 0]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_DefaultPartsReversed() throws Exception {
		router.addResource("{id}/{models}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /(\\w+)/accounts -> AccountController#show(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/0/accounts", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /0/accounts"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[id, 0]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_OnlyParamPart() throws Exception {
		router.addResource("?{name=business}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id,name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts/0"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[id, 0], [name, business]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_CustomPart() throws Exception {
		router.addResource("{models}/{name:\\w+}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(name)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/accounts/personal", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts/business"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[name, business]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_CustomParts() throws Exception {
		router.addResource("{type:\\w+}/{models}/{name:\\w+}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /(\\w+)/accounts/(\\w+) -> AccountController#show(type,name)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/checking/accounts/personal", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /savings/accounts/holiday"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[type, savings], [name, holiday]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Prefix() throws Exception {
		router.addResource("prefix/{models}/{name:\\w+}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /prefix/accounts/(\\w+) -> AccountController#show(name)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/prefix/accounts/personal", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());

		RouteHandler handler = router.getHandler(request("[GET] /prefix/accounts/business"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[name, business]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_Partial() throws Exception {
		router.addResource("{name:\\w+}Account", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /(\\w+)Account -> AccountController#show(name)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/personalAccount", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());

		RouteHandler handler = router.getHandler(request("[GET] /businessAccount"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[name, business]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ParamPartRequired() throws Exception {
		router.addResource("{models}/new?{type:\\w+}", Account.class, Action.showNew);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/new\\?type\\=(\\w+) -> AccountController#showNew(type)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new?type=checking", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts/new?type=savings"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showNew, ch.action);
		assertEquals("[[type, savings]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ParamPartsGiven_Create() throws Exception {
		router.addResource("{models}?{type=checking}{name=personal}", Account.class, Action.create);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[POST] /accounts -> AccountController#create(type=checking,name=personal)", router.getRoutes().get(0).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[POST] /accounts"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(create, ch.action);
		assertEquals("[[type, checking], [name, personal]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ParamPartsGiven_Show() throws Exception {
		router.addResource("{models}/{id}?{type=checking}{name=business}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id,type=checking,name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /accounts/1"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[id, 1], [type, checking], [name, business]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ParamPartsGiven_ShowNew() throws Exception {
		router.addResource("{models}/{id}/new?{type=checking}{name=business}", Account.class, Action.showNew);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+)/new -> AccountController#showNew(id,type=checking,name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/0/new", router.pathTo(account, showNew).toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/1/new"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showNew, ch.action);
		assertEquals("[[id, 1], [type, checking], [name, business]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ModelRenamed_Create() throws Exception {
		router.addResource("{models=registers}", Account.class, Action.create);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[POST] /registers -> AccountController#create", router.getRoutes().get(0).toString());
		assertEquals("/registers", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/registers", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[POST] /registers"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(create, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ModelRenamed_Show() throws Exception {
		router.addResource("{models=registers}/{id}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /registers/(\\w+) -> AccountController#show(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/registers/0", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /registers/1"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[id, 1]]", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoute_ModelRenamed_ShowNew() throws Exception {
		router.addResource("{models=registers}/new", Account.class, Action.showNew);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /registers/new -> AccountController#showNew", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/registers/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/registers/new", router.pathTo(account, showNew).toString());
		
		RouteHandler handler = router.getHandler(request("[GET] /registers/new"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showNew, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testAddModelRoutes() throws Exception {
		router.addResources(Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
		
		router.removeResources(Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutesTwice() throws Exception {
		router.addResources(Account.class);
		router.addResources(Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
		
		router.removeResources(Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_AddShowAll() throws Exception {
		router.addResources(Account.class);
		router.addResource("{models}/{type:\\w+}", Account.class, showAll); // the last one added is the one used
		assertEquals(8, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#showAll(type)",		router.getRoutes().get(7).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts/checking", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
		
		router.removeResource("{models}/{type:\\w+}", Account.class, showAll);
		assertEquals(7, router.getRoutes().size());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		
		router.removeResources(Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddShowAll_AddModelRoutes() throws Exception {
		router.addResource("{models}/{type:\\w+}", Account.class, showAll);
		router.addResources(Account.class);
		assertEquals(8, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#showAll(type)", 		router.getRoutes().get(3).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(4).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(6).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(7).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());

		router.removeResource("{models}/{type:\\w+}", Account.class, showAll);
		assertEquals(7, router.getRoutes().size());

		router.removeResources(Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_Single_Show() throws Exception {
		router.addResources(Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
	}
	
	@Test
	public void testAddModelRoutes_Single_ShowAll() throws Exception {
		router.addResources(Account.class, Action.showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
	}
	
	@Test
	public void testAddModelRoutes_Multiple() throws Exception {
		router.addResources(Account.class, Action.show, Action.showEdit, Action.create);
		assertEquals(3, router.getRoutes().size());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(1).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(2).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
	}
	
	@Test
	public void testAddModelRoutes_WithPrefix() throws Exception {
		router.addResources("prefix", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /prefix/accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /prefix/accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /prefix/accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /prefix/accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /prefix/accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /prefix/accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /prefix/accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/prefix/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/prefix/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/prefix/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/prefix/accounts", router.pathTo(account, create).toString());
		assertEquals("/prefix/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/prefix/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/prefix/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/prefix/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/prefix/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/prefix/accounts/new", router.pathTo(account, showNew).toString());
		
		router.removeResources("prefix", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_MissingId() throws Exception {
		router.addResources("{models}/postmodel", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts/postmodel -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/postmodel/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts/postmodel -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/postmodel/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/postmodel/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/postmodel/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/postmodel/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/accounts/postmodel", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts/postmodel", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/postmodel/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts/postmodel", router.pathTo(account, create).toString());
		assertEquals("/accounts/postmodel/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/postmodel/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/postmodel/0", router.pathTo(account, show).toString());
		assertEquals("/accounts/postmodel", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/postmodel/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/postmodel/new", router.pathTo(account, showNew).toString());
		
		router.removeResources("{models}/postmodel", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_MissingModel() throws Exception {
		router.addResources("prefix/{id}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /prefix/accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /prefix/accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /prefix/accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /prefix/(\\w+)/accounts -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /prefix/(\\w+)/accounts -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /prefix/(\\w+)/accounts -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /prefix/(\\w+)/accounts/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/prefix/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/prefix/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/prefix/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/prefix/accounts", router.pathTo(account, create).toString());
		assertEquals("/prefix/0/accounts", router.pathTo(account, destroy).toString());
		assertEquals("/prefix/0/accounts", router.pathTo(account, update).toString());
		assertEquals("/prefix/0/accounts", router.pathTo(account, show).toString());
		assertEquals("/prefix/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/prefix/0/accounts/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/prefix/accounts/new", router.pathTo(account, showNew).toString());
		
		router.removeResources("prefix/{id}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_DefaultsParts() throws Exception {
		router.addResources("{models}/{id}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
	}
	
	@Test
	public void testAddModelRoutes_DefaultsPartsReversed() throws Exception {
		router.addResources("{id}/{models}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /(\\w+)/accounts -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /(\\w+)/accounts -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /(\\w+)/accounts -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /(\\w+)/accounts/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/0/accounts", router.pathTo(account, destroy).toString());
		assertEquals("/0/accounts", router.pathTo(account, update).toString());
		assertEquals("/0/accounts", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/0/accounts/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
	}
	
	@Test
	public void testAddModelRoutes_CustomId() throws Exception {
		router.addResources("{models}/{id:\\w+}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
		
		router.removeResources("{models}/{id:\\w+}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_CustomPartAndId() throws Exception {
		router.addResources("{name:\\w+}/{models}/{id:\\w+}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[POST] /(\\w+)/accounts -> AccountController#create(name)", 					router.getRoutes().get(0).toString());
		assertEquals("[PUT] /(\\w+)/accounts/(\\w+) -> AccountController#update(name,id)", 			router.getRoutes().get(1).toString());
		assertEquals("[DELETE] /(\\w+)/accounts/(\\w+) -> AccountController#destroy(name,id)", 		router.getRoutes().get(2).toString());
		assertEquals("[GET] /(\\w+)/accounts/(\\w+) -> AccountController#show(name,id)", 			router.getRoutes().get(3).toString());
		assertEquals("[GET] /(\\w+)/accounts -> AccountController#showAll(name)", 					router.getRoutes().get(4).toString());
		assertEquals("[GET] /(\\w+)/accounts/(\\w+)/edit -> AccountController#showEdit(name,id)", 	router.getRoutes().get(5).toString());
		assertEquals("[GET] /(\\w+)/accounts/new -> AccountController#showNew(name)", 				router.getRoutes().get(6).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/personal/accounts", router.pathTo(account, create).toString());
		assertEquals("/personal/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/personal/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/personal/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/personal/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/personal/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/personal/accounts/new", router.pathTo(account, showNew).toString());

		router.removeResources("{name:\\w+}/{models}/{id:\\w+}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_Prefix() throws Exception {
		router.addResources("prefix/{models}/{id}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /prefix/accounts -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /prefix/accounts/new -> AccountController#showNew", 				router.getRoutes().get(1).toString());
		assertEquals("[POST] /prefix/accounts -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /prefix/accounts/(\\w+) -> AccountController#update(id)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /prefix/accounts/(\\w+) -> AccountController#destroy(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /prefix/accounts/(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /prefix/accounts/(\\w+)/edit -> AccountController#showEdit(id)", 	router.getRoutes().get(6).toString());
		assertEquals("/prefix/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/prefix/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/prefix/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/prefix/accounts", router.pathTo(account, create).toString());
		assertEquals("/prefix/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/prefix/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/prefix/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/prefix/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/prefix/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/prefix/accounts/new", router.pathTo(account, showNew).toString());

		router.removeResources("prefix/{models}/{id}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_Partial() throws Exception {
		router.addResources("{models=account}{id}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /account -> AccountController#showAll", 				router.getRoutes().get(0).toString());
		assertEquals("[GET] /account/new -> AccountController#showNew",				router.getRoutes().get(1).toString());
		assertEquals("[POST] /account -> AccountController#create", 				router.getRoutes().get(2).toString());
		assertEquals("[PUT] /account(\\w+) -> AccountController#update(id)", 		router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /account(\\w+) -> AccountController#destroy(id)", 	router.getRoutes().get(4).toString());
		assertEquals("[GET] /account(\\w+) -> AccountController#show(id)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /account(\\w+)/edit -> AccountController#showEdit(id)",	router.getRoutes().get(6).toString());
		assertEquals("/account", router.pathTo(Account.class, create).toString());
		assertEquals("/account", router.pathTo(Account.class, showAll).toString());
		assertEquals("/account/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/account", router.pathTo(account, create).toString());
		assertEquals("/account0", router.pathTo(account, destroy).toString());
		assertEquals("/account0", router.pathTo(account, update).toString());
		assertEquals("/account0", router.pathTo(account, show).toString());
		assertEquals("/account", router.pathTo(account, showAll).toString());
		assertEquals("/account0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/account/new", router.pathTo(account, showNew).toString());

		router.removeResources("{models=account}{id}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_PartialAsId() throws Exception {
		router.addResources("{id=type:\\w+}{models=Account}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /Account -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /Account/new -> AccountController#showNew",					router.getRoutes().get(1).toString());
		assertEquals("[POST] /Account -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /(\\w+)Account -> AccountController#update(type)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /(\\w+)Account -> AccountController#destroy(type)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /(\\w+)Account -> AccountController#show(type)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /(\\w+)Account/edit -> AccountController#showEdit(type)",	router.getRoutes().get(6).toString());
		assertEquals("/Account", router.pathTo(Account.class, create).toString());
		assertEquals("/Account", router.pathTo(Account.class, showAll).toString());
		assertEquals("/Account/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/Account", router.pathTo(account, create).toString());
		assertEquals("/checkingAccount", router.pathTo(account, destroy).toString());
		assertEquals("/checkingAccount", router.pathTo(account, update).toString());
		assertEquals("/checkingAccount", router.pathTo(account, show).toString());
		assertEquals("/Account", router.pathTo(account, showAll).toString());
		assertEquals("/checkingAccount/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/Account/new", router.pathTo(account, showNew).toString());

		router.removeResources("{id=type:\\w+}{models=Account}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_ParamPart() throws Exception {
		router.addResources("{models}/{id}?{name:\\w+}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[POST] /accounts\\?name\\=(\\w+) -> AccountController#create(name)", 					router.getRoutes().get(0).toString());
		assertEquals("[PUT] /accounts/(\\w+)\\?name\\=(\\w+) -> AccountController#update(id,name)", 			router.getRoutes().get(1).toString());
		assertEquals("[DELETE] /accounts/(\\w+)\\?name\\=(\\w+) -> AccountController#destroy(id,name)", 		router.getRoutes().get(2).toString());
		assertEquals("[GET] /accounts/(\\w+)\\?name\\=(\\w+) -> AccountController#show(id,name)", 			router.getRoutes().get(3).toString());
		assertEquals("[GET] /accounts\\?name\\=(\\w+) -> AccountController#showAll(name)", 					router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit\\?name\\=(\\w+) -> AccountController#showEdit(id,name)", 	router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/new\\?name\\=(\\w+) -> AccountController#showNew(name)", 				router.getRoutes().get(6).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts?name=personal", router.pathTo(account, create).toString());
		assertEquals("/accounts/0?name=personal", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0?name=personal", router.pathTo(account, update).toString());
		assertEquals("/accounts/0?name=personal", router.pathTo(account, show).toString());
		assertEquals("/accounts?name=personal", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit?name=personal", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new?name=personal", router.pathTo(account, showNew).toString());

		router.removeResources("{models}/{id}?{name:\\w+}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_ParamPartsGiven() throws Exception {
		router.addResources("{models}/{id}?{type=savings}{name=holiday}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll(type=savings,name=holiday)", 				router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew(type=savings,name=holiday)", 			router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create(type=savings,name=holiday)",					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id,type=savings,name=holiday)", 		router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id,type=savings,name=holiday)", 	router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id,type=savings,name=holiday)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id,type=savings,name=holiday)", router.getRoutes().get(6).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());

		router.removeResources("{models}/{id}?{type=savings}{name=holiday}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testAddModelRoutes_OnlyParamPartsGiven() throws Exception {
		router.addResources("?{type=savings}{name=holiday}", Account.class);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll(type=savings,name=holiday)", 				router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew(type=savings,name=holiday)", 			router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create(type=savings,name=holiday)",					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id,type=savings,name=holiday)", 		router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id,type=savings,name=holiday)", 	router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id,type=savings,name=holiday)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id,type=savings,name=holiday)", router.getRoutes().get(6).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());

		router.removeResources("?{type=savings}{name=holiday}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNamedRoute_InvalidName() throws Exception {
		router.add("my{ models }Account");
	}
	
	@Test
	public void testNamedRoute_Model_Fixed() throws Exception {
		router.add("myAccount").asResource("account1", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /account1 -> AccountController#show", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/account1", router.pathTo("myAccount").toString());

		router.remove("myAccount");
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testNamedRoute_Model_Fixed_Twice() throws Exception {
		router.add("myAccount").asResource("account1", Account.class, Action.show);
		router.add("myAccount").asResource("account1", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /account1 -> AccountController#show", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/account1", router.pathTo("myAccount").toString());

		router.remove("myAccount");
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testNamedRoute_Two_Models_Fixed() throws Exception {
		router.add("myAccount").asResource("account1", Account.class, Action.show);
		router.add("myAccount").asResource("account2", Account.class, Action.show);
		assertEquals(2, router.getRoutes().size());
		assertEquals("[GET] /account1 -> AccountController#show", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/account2", router.pathTo("myAccount").toString());

		router.remove("myAccount");
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testNamedRoute_Model_FixedVar() throws Exception {
		router.add("myAccount").asResource("{models}/{id=0}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/0 -> AccountController#show(id=0)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/accounts/0", router.pathTo("myAccount").toString());
	}
	
	@Test
	public void testNamedRoute_Model_Create() throws Exception {
		router.add("myAccount").asResource(Account.class, create);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[POST] /myAccount -> AccountController#create", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/myAccount", router.pathTo("myAccount").toString());
		assertEquals("/myAccount", router.pathTo("myAccount", account).toString());
	}
	
	@Test
	public void testNamedRoute_Model_Destroy() throws Exception {
		router.add("myAccount").asResource(Account.class, destroy);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[DELETE] /myAccount/(\\w+) -> AccountController#destroy(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/myAccount/0", router.pathTo("myAccount", account).toString());
	}
	
	@Test
	public void testNamedRoute_Model_Update() throws Exception {
		router.add("myAccount").asResource(Account.class, update);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[PUT] /myAccount/(\\w+) -> AccountController#update(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/myAccount/0", router.pathTo("myAccount", account).toString());
	}
	
	@Test
	public void testNamedRoute_Model_Show() throws Exception {
		router.add("myAccount").asResource(Account.class, show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /myAccount/(\\w+) -> AccountController#show(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/myAccount/0", router.pathTo("myAccount", account).toString());
	}
	
	@Test
	public void testNamedRoute_Model_ShowAll() throws Exception {
		router.add("myAccount").asResource(Account.class, showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /myAccount -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/myAccount", router.pathTo("myAccount").toString());
		assertEquals("/myAccount", router.pathTo("myAccount", account).toString());
	}
	
	@Test
	public void testNamedRoute_Model_ShowEdit() throws Exception {
		router.add("myAccount").asResource(Account.class, showEdit);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /myAccount/(\\w+)/edit -> AccountController#showEdit(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/#", router.pathTo("myAccount").toString());
		assertEquals("/myAccount/0/edit", router.pathTo("myAccount", account).toString());
	}
	
	@Test
	public void testNamedRoute_Model_ShowNew() throws Exception {
		router.add("myAccount").asResource(Account.class, showNew);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /myAccount/new -> AccountController#showNew", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/myAccount/new", router.pathTo("myAccount").toString());
		assertEquals("/myAccount/new", router.pathTo("myAccount", account).toString());
	}
	
	@Test
	public void testNamedRoute_Model() throws Exception {
		router.add("myAccount").asResource(Account.class, showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /myAccount -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/myAccount", router.pathTo("myAccount").toString());
	}
	
	@Test
	public void testNamedRoute_Model_WithParams() throws Exception {
		router.add("myAccount").asResource("{models}/show?{name=business}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/show -> AccountController#show(name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/accounts/show", router.pathTo("myAccount").toString());
	}
	
	@Test
	public void testNamedRoute_Model_OnlyParams() throws Exception {
		router.add("myAccount").asResource("?{name=business}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#show(name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/accounts", router.pathTo("myAccount").toString());
	}
	
	@Test
	public void testNestedRoutes() throws Exception {
		Member member = new Member();
		Phone phone = new Phone();
		router.addResources(Member.class).hasMany("phones");
		assertEquals(10, router.getRoutes().size());
		assertEquals("[GET] /members -> MemberController#showAll", 								router.getRoutes().get(0).toString());
		assertEquals("[GET] /members/new -> MemberController#showNew", 							router.getRoutes().get(1).toString());
		assertEquals("[POST] /members -> MemberController#create", 								router.getRoutes().get(2).toString());
		assertEquals("[PUT] /members/(\\w+) -> MemberController#update(id)", 					router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /members/(\\w+) -> MemberController#destroy(id)", 				router.getRoutes().get(4).toString());
		assertEquals("[GET] /members/(\\w+) -> MemberController#show(id)", 						router.getRoutes().get(5).toString());
		assertEquals("[GET] /members/(\\w+)/edit -> MemberController#showEdit(id)", 			router.getRoutes().get(6).toString());
		assertEquals("[POST] /members/(\\w+)/phones -> PhoneController#create(member[id])", 	router.getRoutes().get(7).toString());
		assertEquals("[GET] /members/(\\w+)/phones -> PhoneController#showAll(member[id])", 	router.getRoutes().get(8).toString());
		assertEquals("[GET] /members/(\\w+)/phones/new -> PhoneController#showNew(member[id])",	router.getRoutes().get(9).toString());
		assertEquals("/members", router.pathTo(Member.class, create).toString());
		assertEquals("/members", router.pathTo(Member.class, showAll).toString());
		assertEquals("/members/new", router.pathTo(Member.class, showNew).toString());
		assertEquals("/members", router.pathTo(member, create).toString());
		assertEquals("/members/0", router.pathTo(member, destroy).toString());
		assertEquals("/members/0", router.pathTo(member, update).toString());
		assertEquals("/members/0", router.pathTo(member, show).toString());
		assertEquals("/members", router.pathTo(member, showAll).toString());
		assertEquals("/members/0/edit", router.pathTo(member, showEdit).toString());
		assertEquals("/members/new", router.pathTo(member, showNew).toString());
		assertEquals("/#", router.pathTo(Phone.class, create).toString());
		assertEquals("/#", router.pathTo(Phone.class, showAll).toString());
		assertEquals("/#", router.pathTo(Phone.class, showNew).toString());
		assertEquals("/#", router.pathTo(phone, create).toString());
		assertEquals("/#", router.pathTo(phone, destroy).toString());
		assertEquals("/#", router.pathTo(phone, update).toString());
		assertEquals("/#", router.pathTo(phone, show).toString());
		assertEquals("/#", router.pathTo(phone, showAll).toString());
		assertEquals("/#", router.pathTo(phone, showEdit).toString());
		assertEquals("/#", router.pathTo(phone, showNew).toString());
		assertEquals("/members/0/phones", router.pathTo(member, "phones", create).toString());
		assertEquals("/members/0/phones", router.pathTo(member, "phones", showAll).toString());
		assertEquals("/members/0/phones/new", router.pathTo(member, "phones", showNew).toString());

		Request request = request("[GET] /members/1/phones");
		
		RouteHandler handler = router.getHandler(request);
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(PhoneController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals(Member.class, ch.parentClass);
		assertEquals("phones", ch.parentField);
		assertEquals("[[member[id], 1]]", asString(ch.params));

		HttpController c = ch.getController(request);
		
		Map<String, Object> query = c.getQuery();
		Object from = query.remove("$from");
		assertTrue(query.isEmpty());
		assertTrue(from instanceof Map);
		assertEquals(Member.class, ((Map<?,?>) from).get("$type"));
		assertEquals("1", ((Map<?,?>) from).get("$id"));
		assertEquals("phones", ((Map<?,?>) from).get("$field"));
	}
	
	@Test
	public void testNestedRoutes_Bidi() throws Exception {
		router.addResources(Account.class).hasMany("categories");
		assertEquals(10, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#showAll", 						router.getRoutes().get(0).toString());
		assertEquals("[GET] /accounts/new -> AccountController#showNew", 					router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 						router.getRoutes().get(2).toString());
		assertEquals("[PUT] /accounts/(\\w+) -> AccountController#update(id)", 				router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /accounts/(\\w+) -> AccountController#destroy(id)", 			router.getRoutes().get(4).toString());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", 				router.getRoutes().get(5).toString());
		assertEquals("[GET] /accounts/(\\w+)/edit -> AccountController#showEdit(id)", 		router.getRoutes().get(6).toString());
		assertEquals("[POST] /accounts/(\\w+)/categories -> CategoryController#create(category[cAccount])", 	router.getRoutes().get(7).toString());
		assertEquals("[GET] /accounts/(\\w+)/categories -> CategoryController#showAll(account[id])", 			router.getRoutes().get(8).toString());
		assertEquals("[GET] /accounts/(\\w+)/categories/new -> CategoryController#showNew(category[cAccount])",	router.getRoutes().get(9).toString());
		assertEquals("/accounts", router.pathTo(Account.class, create).toString());
		assertEquals("/accounts", router.pathTo(Account.class, showAll).toString());
		assertEquals("/accounts/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/accounts", router.pathTo(account, create).toString());
		assertEquals("/accounts/0", router.pathTo(account, destroy).toString());
		assertEquals("/accounts/0", router.pathTo(account, update).toString());
		assertEquals("/accounts/0", router.pathTo(account, show).toString());
		assertEquals("/accounts", router.pathTo(account, showAll).toString());
		assertEquals("/accounts/0/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/accounts/new", router.pathTo(account, showNew).toString());
		assertEquals("/#", router.pathTo(Category.class, create).toString());
		assertEquals("/#", router.pathTo(Category.class, showAll).toString());
		assertEquals("/#", router.pathTo(Category.class, showNew).toString());
		assertEquals("/#", router.pathTo(category, create).toString());
		assertEquals("/#", router.pathTo(category, destroy).toString());
		assertEquals("/#", router.pathTo(category, update).toString());
		assertEquals("/#", router.pathTo(category, show).toString());
		assertEquals("/#", router.pathTo(category, showAll).toString());
		assertEquals("/#", router.pathTo(category, showEdit).toString());
		assertEquals("/#", router.pathTo(category, showNew).toString());
		assertEquals("/accounts/0/categories", router.pathTo(account, "categories", create).toString());
		assertEquals("/accounts/0/categories", router.pathTo(account, "categories", showAll).toString());
		assertEquals("/accounts/0/categories/new", router.pathTo(account, "categories", showNew).toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/1/categories"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(CategoryController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals("[[account[id], 1]]", asString(ch.params));

		handler = router.getHandler(request("[GET] /accounts/1/categories/new"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		ch = (HttpHandler) handler;
		assertEquals(CategoryController.class, ch.controllerClass);
		assertEquals(showNew, ch.action);
		assertEquals(Account.class, ch.parentClass);
		assertEquals("categories", ch.parentField);
		assertEquals("[[category[cAccount], 1]]", asString(ch.params));
	}
	
	@Test
	public void testNestedRoutes_CustomId() throws Exception {
		Member member = new Member();
		Phone phone = new Phone();
		router.addResources("{id=type:\\w+}{models=Member}", Member.class).hasMany("phones");
		assertEquals(10, router.getRoutes().size());
		assertEquals("[GET] /Member -> MemberController#showAll", 								router.getRoutes().get(0).toString());
		assertEquals("[GET] /Member/new -> MemberController#showNew",							router.getRoutes().get(1).toString());
		assertEquals("[POST] /Member -> MemberController#create", 								router.getRoutes().get(2).toString());
		assertEquals("[PUT] /(\\w+)Member -> MemberController#update(type)", 					router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /(\\w+)Member -> MemberController#destroy(type)", 				router.getRoutes().get(4).toString());
		assertEquals("[GET] /(\\w+)Member -> MemberController#show(type)", 						router.getRoutes().get(5).toString());
		assertEquals("[GET] /(\\w+)Member/edit -> MemberController#showEdit(type)",				router.getRoutes().get(6).toString());
		assertEquals("[POST] /(\\w+)Member/phones -> PhoneController#create(member[type])", 	router.getRoutes().get(7).toString());
		assertEquals("[GET] /(\\w+)Member/phones -> PhoneController#showAll(member[type])", 	router.getRoutes().get(8).toString());
		assertEquals("[GET] /(\\w+)Member/phones/new -> PhoneController#showNew(member[type])",	router.getRoutes().get(9).toString());
		assertEquals("/Member", router.pathTo(Member.class, create).toString());
		assertEquals("/Member", router.pathTo(Member.class, showAll).toString());
		assertEquals("/Member/new", router.pathTo(Member.class, showNew).toString());
		assertEquals("/Member", router.pathTo(member, create).toString());
		assertEquals("/adminMember", router.pathTo(member, destroy).toString());
		assertEquals("/adminMember", router.pathTo(member, update).toString());
		assertEquals("/adminMember", router.pathTo(member, show).toString());
		assertEquals("/Member", router.pathTo(member, showAll).toString());
		assertEquals("/adminMember/edit", router.pathTo(member, showEdit).toString());
		assertEquals("/Member/new", router.pathTo(member, showNew).toString());
		assertEquals("/#", router.pathTo(Phone.class, create).toString());
		assertEquals("/#", router.pathTo(Phone.class, showAll).toString());
		assertEquals("/#", router.pathTo(Phone.class, showNew).toString());
		assertEquals("/#", router.pathTo(phone, create).toString());
		assertEquals("/#", router.pathTo(phone, destroy).toString());
		assertEquals("/#", router.pathTo(phone, update).toString());
		assertEquals("/#", router.pathTo(phone, show).toString());
		assertEquals("/#", router.pathTo(phone, showAll).toString());
		assertEquals("/#", router.pathTo(phone, showEdit).toString());
		assertEquals("/#", router.pathTo(phone, showNew).toString());
		assertEquals("/adminMember/phones", router.pathTo(member, "phones", create).toString());
		assertEquals("/adminMember/phones", router.pathTo(member, "phones", showAll).toString());
		assertEquals("/adminMember/phones/new", router.pathTo(member, "phones", showNew).toString());

		RouteHandler handler = router.getHandler(request("[GET] /adminMember/phones"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(PhoneController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals(Member.class, ch.parentClass);
		assertEquals("phones", ch.parentField);
		assertEquals("[[member[type], admin]]", asString(ch.params));

		router.removeResources("{id=type:\\w+}{models=Member}", Member.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testNestedRoutes_CustomId_Bidi() throws Exception {
		router.addResources("{id=type:\\w+}{models=Account}", Account.class).hasMany("categories");
		assertEquals(10, router.getRoutes().size());
		assertEquals("[GET] /Account -> AccountController#showAll", 					router.getRoutes().get(0).toString());
		assertEquals("[GET] /Account/new -> AccountController#showNew",					router.getRoutes().get(1).toString());
		assertEquals("[POST] /Account -> AccountController#create", 					router.getRoutes().get(2).toString());
		assertEquals("[PUT] /(\\w+)Account -> AccountController#update(type)", 			router.getRoutes().get(3).toString());
		assertEquals("[DELETE] /(\\w+)Account -> AccountController#destroy(type)", 		router.getRoutes().get(4).toString());
		assertEquals("[GET] /(\\w+)Account -> AccountController#show(type)", 			router.getRoutes().get(5).toString());
		assertEquals("[GET] /(\\w+)Account/edit -> AccountController#showEdit(type)",	router.getRoutes().get(6).toString());
		assertEquals("[POST] /(\\w+)Account/categories -> CategoryController#create(category[cAccount][type])", 	router.getRoutes().get(7).toString());
		assertEquals("[GET] /(\\w+)Account/categories -> CategoryController#showAll(account[type])", 				router.getRoutes().get(8).toString());
		assertEquals("[GET] /(\\w+)Account/categories/new -> CategoryController#showNew(category[cAccount][type])",	router.getRoutes().get(9).toString());
		assertEquals("/Account", router.pathTo(Account.class, create).toString());
		assertEquals("/Account", router.pathTo(Account.class, showAll).toString());
		assertEquals("/Account/new", router.pathTo(Account.class, showNew).toString());
		assertEquals("/Account", router.pathTo(account, create).toString());
		assertEquals("/checkingAccount", router.pathTo(account, destroy).toString());
		assertEquals("/checkingAccount", router.pathTo(account, update).toString());
		assertEquals("/checkingAccount", router.pathTo(account, show).toString());
		assertEquals("/Account", router.pathTo(account, showAll).toString());
		assertEquals("/checkingAccount/edit", router.pathTo(account, showEdit).toString());
		assertEquals("/Account/new", router.pathTo(account, showNew).toString());
		assertEquals("/#", router.pathTo(Category.class, create).toString());
		assertEquals("/#", router.pathTo(Category.class, showAll).toString());
		assertEquals("/#", router.pathTo(Category.class, showNew).toString());
		assertEquals("/#", router.pathTo(category, create).toString());
		assertEquals("/#", router.pathTo(category, destroy).toString());
		assertEquals("/#", router.pathTo(category, update).toString());
		assertEquals("/#", router.pathTo(category, show).toString());
		assertEquals("/#", router.pathTo(category, showAll).toString());
		assertEquals("/#", router.pathTo(category, showEdit).toString());
		assertEquals("/#", router.pathTo(category, showNew).toString());
		assertEquals("/checkingAccount/categories", router.pathTo(account, "categories", create).toString());
		assertEquals("/checkingAccount/categories", router.pathTo(account, "categories", showAll).toString());
		assertEquals("/checkingAccount/categories/new", router.pathTo(account, "categories", showNew).toString());

		RouteHandler handler = router.getHandler(request("[GET] /checkingAccount/categories"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(CategoryController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals("[[account[type], checking]]", asString(ch.params));

		handler = router.getHandler(request("[POST] /checkingAccount/categories"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		ch = (HttpHandler) handler;
		assertEquals(CategoryController.class, ch.controllerClass);
		assertEquals(create, ch.action);
		assertEquals(Account.class, ch.parentClass);
		assertEquals("categories", ch.parentField);
		assertEquals("[[category[cAccount][type], checking]]", asString(ch.params));

		router.removeResources("{id=type:\\w+}{models=Account}", Account.class);
		assertEquals(0, router.getRoutes().size());
	}
	
	@Test
	public void testNamedRoute_Controller() throws Exception {
		router.add("my_account").asResource(Account.class, showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_account -> AccountController#showAll", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/my_account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(showAll, ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testNamedRoute_Controller_FixedAndWithVariable() throws Exception {
		router.add("my_account").asResource("accounts/{name:\\w+}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(name)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/#", router.pathTo("my_account").toString());
		assertEquals("/accounts/personal", router.pathTo("my_account", "personal").toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/personal"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[name, personal]]", asString(ch.params));
	}
	
	@Test
	public void testNamedRoute_Controller_FixedAndWithIdVariable() throws Exception {
		router.add("my_account").asResource("accounts/{id}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/(\\w+) -> AccountController#show(id)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/#", router.pathTo("my_account").toString());
		assertEquals("/accounts/1", router.pathTo("my_account", 1).toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/1"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[id, 1]]", asString(ch.params));
	}
	
	@Test
	public void testNamedRoute_Controller_FixedAndWithInvalidVariable() throws Exception {
		router.add("my_account").asResource("accounts/{name:\\w+}", Account.class, Action.show);
		assertEquals("/#", router.pathTo("my_account", 1.0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/1.0"));
		assertNull(handler);
	}
	
	@Test
	public void testNamedRoute_Controller_FixedAndWithInvalidIdVariable() throws Exception {
		router.add("my_account").asResource("accounts/{id}", Account.class, Action.show);
		assertEquals("/#", router.pathTo("my_account", "bob").toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/b!ob"));
		assertNull(handler);
	}
	
	@Test
	public void testNamedRoute_Controller_FixedAndWithParams() throws Exception {
		router.add("my_account").asResource("accounts/show?{name=business}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/show -> AccountController#show(name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/accounts/show", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/show"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[name, business]]", asString(ch.params));
	}
	
	@Test
	public void testNamedRoute_Controller_OnlyParams() throws Exception {
		router.add("my_account").asResource("?{name=business}", Account.class, Action.show);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts -> AccountController#show(name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/accounts", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals(show, ch.action);
		assertEquals("[[name, business]]", asString(ch.params));
	}
	
	@Test
	public void testNamedRoute_Controller_NonREST() throws Exception {
		router.add("my_account").asRoute(GET, AccountController.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_account -> AccountController#handleRequest", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/my_account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertNull(ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testNamedRoute_Controller_NonREST_WithRule() throws Exception {
		router.add("my_account").asRoute(GET, "account", AccountController.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /account -> AccountController#handleRequest", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertNull(ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Ignore
	@Test
	public void testNamedRoute_Controller_NonREST_Multi() throws Exception {
//		router.add("my_account").asRoutes(AccountController.class, GET, POST);
		assertEquals(2, router.getRoutes().size());
		assertEquals("[GET] /my_account -> AccountController#handleRequest", router.getRoutes().get(0).toString());
		assertEquals("[POST] /my_account -> AccountController#handleRequest", router.getRoutes().get(1).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/my_account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertNull(ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Ignore
	@Test
	public void testNamedRoute_Controller_NonREST_Empty() throws Exception {
//		router.add("my_account").asRoutes(AccountController.class);
		assertEquals(5, router.getRoutes().size());
		assertEquals("[DELETE] /my_account -> AccountController#handleRequest", router.getRoutes().get(0).toString());
		assertEquals("[GET] /my_account -> AccountController#handleRequest", router.getRoutes().get(1).toString());
		assertEquals("[HEAD] /my_account -> AccountController#handleRequest", router.getRoutes().get(2).toString());
		assertEquals("[POST] /my_account -> AccountController#handleRequest", router.getRoutes().get(3).toString());
		assertEquals("[PUT] /my_account -> AccountController#handleRequest", router.getRoutes().get(4).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/my_account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertNull(ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Ignore
	@Test
	public void testNamedRoute_Controller_NonREST_Multi_WithRule() throws Exception {
//		router.add("my_account").asRoutes("account", AccountController.class, GET, POST);
		assertEquals(2, router.getRoutes().size());
		assertEquals("[GET] /account -> AccountController#handleRequest", router.getRoutes().get(0).toString());
		assertEquals("[POST] /account -> AccountController#handleRequest", router.getRoutes().get(1).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertNull(ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Test
	public void testNamedRoute_Controller_NonREST_Wildcard() throws Exception {
		router.add("my_account").asRoute(GET, "/account/*", AccountController.class);
		assertEquals(3, router.getRoutes().size());
		assertEquals("[GET] /account -> AccountController#handleRequest", router.getRoutes().get(0).toString());
		assertEquals("[GET] /account/(.*) -> AccountController#handleRequest", router.getRoutes().get(1).toString());
		assertEquals("[GET] /account/(.*)\\?(.*) -> AccountController#handleRequest", router.getRoutes().get(2).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertNull(ch.action);
		assertEquals("null", asString(ch.params));
	}
	
	@Ignore
	@Test
	public void testNamedRoute_Controller_NonREST_Multi_Wildcard() throws Exception {
//		router.add("my_account").asRoutes("/account/*", AccountController.class);
		assertEquals(15, router.getRoutes().size());
		int i = 0;
		assertEquals("[DELETE] /account -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[GET] /account -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[HEAD] /account -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[POST] /account -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[PUT] /account -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[GET] /account/(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[GET] /account/(.*)\\?(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[POST] /account/(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[POST] /account/(.*)\\?(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[PUT] /account/(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[PUT] /account/(.*)\\?(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[DELETE] /account/(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[DELETE] /account/(.*)\\?(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[HEAD] /account/(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("[HEAD] /account/(.*)\\?(.*) -> AccountController#handleRequest", router.getRoutes().get(i++).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /account"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertNull(ch.action);
		assertEquals("null", asString(ch.params));
	}

//	@Test(expected=IllegalArgumentException.class)
//	public void testNamedRoute_Controller_NonREST_InvalidWildcard() throws Exception {
//		router.add("my_account").asRoutes("/account*", AccountController.class);
//	}

	@Test
	public void testNamedRoute_View() throws Exception {
		router.add("my_account").asView(AccountView.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_account -> AccountView", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/my_account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(ViewHandler.class, handler.getClass());
		ViewHandler vh = (ViewHandler) handler;
		assertEquals(AccountView.class, vh.viewClass);
		assertEquals("null", asString(vh.params));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testNamedRoute_View_InvalidClass() throws Exception {
		router.add("my_account").asView(InvalidView.class);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testNamedRoute_View_InvalidClass_ValidPath() throws Exception {
		// cannot add a view class as a named route
		router.add("my_account").asView("account", InvalidView.class);
	}
	
//	@Test(expected=IllegalArgumentException.class)
//	public void testNamedRoute_View_InvalidArgument() throws Exception {
//		// cannot add a view class and specify an action argument
//		router.add("my_account").asResource("{models}/show?{name=business}", AccountView.class, Action.show);
//	}
	
	@Test
	public void testNamedRoute_View_FixedAndWithParams() throws Exception {
		router.add("my_account").asView("accounts/show?{name=business}", AccountView.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /accounts/show -> AccountView(name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/accounts/show", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /accounts/show"));
		assertNotNull(handler);
		assertEquals(ViewHandler.class, handler.getClass());
		ViewHandler vh = (ViewHandler) handler;
		assertEquals(AccountView.class, vh.viewClass);
		assertEquals("[[name, business]]", asString(vh.params));
	}
	
	@Test
	public void testNamedRoute_View_OnlyParams() throws Exception {
		router.add("my_account").asView("?{name=business}", AccountView.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_account -> AccountView(name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/my_account", router.pathTo("my_account").toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_account"));
		assertNotNull(handler);
		assertEquals(ViewHandler.class, handler.getClass());
		ViewHandler vh = (ViewHandler) handler;
		assertEquals(AccountView.class, vh.viewClass);
		assertEquals("[[name, business]]", asString(vh.params));
	}

	@Test
	public void testRouteStyleSheet() throws Exception {
		router.addStyleSheet(AccountStyles.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /org/oobium/app/routing/router_tests/account_styles.css -> AccountStyles", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /org/oobium/app/routing/router_tests/account_styles.css"));
		assertNotNull(handler);
		assertEquals(StyleSheetHandler.class, handler.getClass());
		StyleSheetHandler dah = (StyleSheetHandler) handler;
		assertEquals(AccountStyles.class, dah.assetClass);
		assertEquals("null", asString(dah.params));
	}
	
	@Test
	public void testGetPaths() throws Exception {
		router.addResources(Account.class);
		System.out.println(router.getPaths());
	}
	
	@Test
	public void testGetModelRouteMap() throws Exception {
		router.addResources(Account.class);
		Map<String, Map<String, Map<String, String>>> map = router.getModelRouteMap();
		assertEquals(1, map.size());
		assertEquals(7, map.get(Account.class.getName()).size());
	}

	@Test
	public void testGetModelRouteMap_WithNestedRoutes() throws Exception {
		router.addResources(Account.class).hasMany("categories").publish();
		Map<String, Map<String, Map<String, String>>> map = router.getModelRouteMap();
		assertEquals(1, map.size());
		assertEquals(10, map.get(Account.class.getName()).size());

		Set<Route> routes = router.published;
		System.out.println(routes);
	}

	@Test
	public void testGetModelRouteMap_WithNestedRoutes1() throws Exception {
		router.addResources(Category.class);
		router.addResources(Account.class).hasMany("categories").publish();
		Map<String, Map<String, Map<String, String>>> map = router.getModelRouteMap();
		assertEquals(2, map.size());
		assertEquals(10, map.get(Account.class.getName()).size());
		assertEquals(7, map.get(Category.class.getName()).size());
		
		Set<Route> routes = router.published;
		System.out.println(routes);
	}

	@Test
	public void testPublish() throws Exception {
		router.setApi("paths");
		router.addStyleSheet(AccountStyles.class).publish();
	}

	@Test
	public void testImpliedNamedRoute() throws Exception {
		router.addRoute(GET, "/test/route/{name=business}", AccountController.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /test/route/business -> AccountController#handleRequest(name=business)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/test/route/business", router.pathTo("getTestRouteName").toString());

		RouteHandler handler = router.getHandler(request("[GET] /test/route/business"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals("[[name, business]]", asString(ch.params));
	}
	
	@Test
	public void testImpliedNamedRoute_PseudoRest() throws Exception {
		router.addRoute("{types:[\\w_]+}", AccountController.class, showAll);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /([\\w_]+) -> AccountController#showAll(types)", router.getRoutes().get(0).toString());
		assertEquals("/#", router.pathTo(Account.class, create).toString());
		assertEquals("/#", router.pathTo(Account.class, showAll).toString());
		assertEquals("/#", router.pathTo(Account.class, showNew).toString());
		assertEquals("/#", router.pathTo(account, create).toString());
		assertEquals("/#", router.pathTo(account, destroy).toString());
		assertEquals("/#", router.pathTo(account, update).toString());
		assertEquals("/#", router.pathTo(account, show).toString());
		assertEquals("/#", router.pathTo(account, showAll).toString());
		assertEquals("/#", router.pathTo(account, showEdit).toString());
		assertEquals("/#", router.pathTo(account, showNew).toString());
		assertEquals("/#", router.pathTo("showAllTypes").toString());
		assertEquals("/business", router.pathTo("showAllTypes", "business").toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_business"));
		assertNotNull(handler);
		assertEquals(HttpHandler.class, handler.getClass());
		HttpHandler ch = (HttpHandler) handler;
		assertEquals(AccountController.class, ch.controllerClass);
		assertEquals("[[types, my_business]]", asString(ch.params));
	}

	@Test
	public void testAddRedirect() throws Exception {
		router.addRedirect("old_path", "new_path");
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /old_path -> /new_path", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /old_path"));
		assertNotNull(handler);
		assertEquals(RedirectHandler.class, handler.getClass());
		RedirectHandler ch = (RedirectHandler) handler;
		assertEquals("/new_path", ch.to);
		assertNull(ch.params); // this will change if we enable params in the future
		
		router.removeRedirect("old_path");
		assertTrue(router.getRoutes().isEmpty());
	}
	
	@Test
	public void testAddView() throws Exception {
		router.addView(MyView.class);
		assertEquals(1, router.getRoutes().size());
		assertEquals("[GET] /my_view -> MyView", router.getRoutes().get(0).toString());

		RouteHandler handler = router.getHandler(request("[GET] /my_view"));
		assertNotNull(handler);
		assertEquals(ViewHandler.class, handler.getClass());
		ViewHandler vh = (ViewHandler) handler;
		assertEquals(MyView.class, vh.viewClass);
		assertNull(vh.params);
	}
	
	@Test
	public void testAddView_WithPath() throws Exception {
		router.addView("/business?{type=business}", AccountView.class);
		router.addView("/personal?{type=personal}", AccountView.class);
		assertEquals(2, router.getRoutes().size());
		assertEquals("[GET] /business -> AccountView(type=business)", router.getRoutes().get(0).toString());
		assertEquals("[GET] /personal -> AccountView(type=personal)", router.getRoutes().get(1).toString());

		RouteHandler handler = router.getHandler(request("[GET] /business"));
		assertNotNull(handler);
		assertEquals(ViewHandler.class, handler.getClass());
		ViewHandler vh = (ViewHandler) handler;
		assertEquals(AccountView.class, vh.viewClass);
		assertEquals("[[type, business]]", asString(vh.params));
	}
	
	@Test
	public void testAddModelRoutesFromJson() throws Exception {
		String json = 
			"[" +
			"  {" +
			"    model:  \"" + Account.class.getName() + "\"," +
			"    action: \"create\"" +
			"  }," +
			"  {" +
			"    model:  \"" + Category.class.getName() + "\"," +
			"    action: \"showAll\"" +
			"  }," +
			"  {" +
			"    model:  \"" + Member.class.getName() + "\"," +
			"    actions: \"create, update, destroy, show, showAll\"" +
			"  }" +
			"]";
		router.addFromJson(json);
		assertEquals(7, router.getRoutes().size());
		assertEquals("[GET] /categories -> CategoryController#showAll", 			router.getRoutes().get(0).toString());
		assertEquals("[GET] /members -> MemberController#showAll", 					router.getRoutes().get(1).toString());
		assertEquals("[POST] /accounts -> AccountController#create", 				router.getRoutes().get(2).toString());
		assertEquals("[POST] /members -> MemberController#create", 					router.getRoutes().get(3).toString());
		assertEquals("[PUT] /members/(\\w+) -> MemberController#update(id)", 		router.getRoutes().get(4).toString());
		assertEquals("[DELETE] /members/(\\w+) -> MemberController#destroy(id)", 	router.getRoutes().get(5).toString());
		assertEquals("[GET] /members/(\\w+) -> MemberController#show(id)", 				router.getRoutes().get(6).toString());
	}
	
}
