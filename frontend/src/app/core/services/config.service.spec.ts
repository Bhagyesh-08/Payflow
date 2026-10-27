import { TestBed } from '@angular/core/testing';
import { ConfigService } from './config.service';

describe('ConfigService', () => {
  let service: ConfigService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ConfigService);
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start with no configuration', () => {
    expect(service.isConfigured).toBeFalse();
    expect(service.config).toBeNull();
  });

  it('should save and retrieve configuration', () => {
    service.setConfig(1, 'test-api-key');
    
    expect(service.isConfigured).toBeTrue();
    expect(service.config?.merchantId).toBe(1);
    expect(service.config?.apiKey).toBe('test-api-key');
  });

  it('should clear configuration', () => {
    service.setConfig(1, 'test-api-key');
    service.clearConfig();
    
    expect(service.isConfigured).toBeFalse();
    expect(service.config).toBeNull();
  });

  it('should persist configuration to sessionStorage', () => {
    service.setConfig(1, 'test-api-key');
    
    const stored = sessionStorage.getItem('payflow_config');
    expect(stored).toBeTruthy();
    
    const parsed = JSON.parse(stored!);
    expect(parsed.merchantId).toBe(1);
    expect(parsed.apiKey).toBe('test-api-key');
  });

  it('should load configuration from sessionStorage', () => {
    // Clear any existing service and set up storage
    sessionStorage.clear();
    sessionStorage.setItem('payflow_config', JSON.stringify({
      merchantId: 2,
      apiKey: 'stored-key'
    }));

    // Create a fresh service instance that will load from storage
    const freshService = TestBed.runInInjectionContext(() => new ConfigService());
    
    expect(freshService.isConfigured).toBeTrue();
    expect(freshService.config?.merchantId).toBe(2);
    expect(freshService.config?.apiKey).toBe('stored-key');
  });
});
