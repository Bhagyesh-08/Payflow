import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ConfigService } from '../services/config.service';

/**
 * Attaches X-API-Key header to API requests
 */
export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  // Only attach to API requests
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }

  const configService = inject(ConfigService);
  const config = configService.config;

  if (!config?.apiKey) {
    return next(req);
  }

  const cloned = req.clone({
    setHeaders: {
      'X-API-Key': config.apiKey
    }
  });

  return next(cloned);
};
