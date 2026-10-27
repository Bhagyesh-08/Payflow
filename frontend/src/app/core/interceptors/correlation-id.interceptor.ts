import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Generates correlation IDs for requests and preserves them in error responses
 */
export const correlationIdInterceptor: HttpInterceptorFn = (req, next) => {
  const correlationId = crypto.randomUUID();

  const cloned = req.clone({
    setHeaders: {
      'X-Correlation-ID': correlationId
    }
  });

  return next(cloned).pipe(
    catchError((error: HttpErrorResponse) => {
      // Preserve correlation ID in error for debugging
      const enhancedError = {
        ...error,
        correlationId: error.headers?.get('X-Correlation-ID') || correlationId
      };
      
      console.error('[HTTP Error]', {
        status: error.status,
        url: req.url,
        correlationId,
        message: error.error?.message || error.message
      });

      return throwError(() => enhancedError);
    })
  );
};
