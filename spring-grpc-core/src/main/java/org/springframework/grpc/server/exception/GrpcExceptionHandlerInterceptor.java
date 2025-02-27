/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.grpc.server.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * A gRPC {@link ServerInterceptor} that handles exceptions thrown during the processing
 * of gRPC calls. It intercepts the call and wraps the {@link ServerCall.Listener} with an
 * {@link ExceptionHandlerListener} that catches exceptions in {@code onMessage} and
 * {@code onHalfClose} methods, and delegates the exception handling to the provided
 * {@link GrpcExceptionHandler}.
 *
 * <p>
 * A fallback mechanism is used to return UNONOWN in case the {@link GrpcExceptionHandler}
 * returns a null.
 *
 * @author Dave Syer
 * @see ServerInterceptor
 * @see GrpcExceptionHandler
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class GrpcExceptionHandlerInterceptor implements ServerInterceptor {

	private final GrpcExceptionHandler exceptionHandler;

	public GrpcExceptionHandlerInterceptor(GrpcExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * Intercepts a gRPC server call to handle exceptions.
	 * @param <ReqT> the type of the request message
	 * @param <RespT> the type of the response message
	 * @param call the server call object
	 * @param headers the metadata headers for the call
	 * @param next the next server call handler in the interceptor chain
	 * @return a listener for the request messages
	 */
	@Override
	public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {
		return new ExceptionHandlerListener<>(next.startCall(call, headers), call,
				new FallbackHandler(this.exceptionHandler));
	}

	static class ExceptionHandlerListener<ReqT, RespT> extends SimpleForwardingServerCallListener<ReqT> {

		private ServerCall<ReqT, RespT> call;

		private GrpcExceptionHandler exceptionHandler;

		ExceptionHandlerListener(ServerCall.Listener<ReqT> delegate, ServerCall<ReqT, RespT> call,
				GrpcExceptionHandler exceptionHandler) {
			super(delegate);
			this.call = call;
			this.exceptionHandler = exceptionHandler;
		}

		@Override
		public void onMessage(ReqT message) {
			try {
				super.onMessage(message);
			}
			catch (Throwable t) {
				this.call.close(this.exceptionHandler.handleException(t), new Metadata());
			}
		}

		@Override
		public void onHalfClose() {
			try {
				super.onHalfClose();
			}
			catch (Throwable t) {
				this.call.close(this.exceptionHandler.handleException(t), new Metadata());
			}
		}

	}

	static class FallbackHandler implements GrpcExceptionHandler {

		private final GrpcExceptionHandler exceptionHandler;

		FallbackHandler(GrpcExceptionHandler exceptionHandler) {
			this.exceptionHandler = exceptionHandler;
		}

		@Override
		public Status handleException(Throwable exception) {
			Status status = this.exceptionHandler.handleException(exception);
			return status != null ? status : Status.fromThrowable(exception);
		}

	}

}
