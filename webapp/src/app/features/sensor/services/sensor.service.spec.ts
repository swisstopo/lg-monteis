import { TestBed } from '@angular/core/testing';
import { SensorControllerService } from '@core/generated';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SensorService } from './sensor.service';

describe('SensorService', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let service: SensorService;

  beforeEach(() => {
    api = {
      findAllFormulas: vi.fn(() => of([])),
      findAllTypes: vi.fn(() => of([])),
      refreshFromFulcrum: vi.fn(() => of(null)),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: SensorControllerService, useValue: api }],
    });
    service = TestBed.inject(SensorService);
  });

  it('refreshes the given sensors from Fulcrum and reloads the table', async () => {
    await service.refreshFromFulcrum(['sensor-1', 'sensor-2']);

    expect(api['refreshFromFulcrum']).toHaveBeenCalledWith({ sensorIds: ['sensor-1', 'sensor-2'] });
    expect(service.sensorsSaved()).toBe(1);
  });

  it('refreshes every visible sensor when none is given, sending no ids at all', async () => {
    await service.refreshFromFulcrum([]);

    expect(api['refreshFromFulcrum']).toHaveBeenCalledWith({});
  });

  it('reloads the table after a failed refresh too, it may have refreshed some sensors', async () => {
    const failure = new Error('502');
    api['refreshFromFulcrum'].mockReturnValue(throwError(() => failure));

    await expect(service.refreshFromFulcrum(['sensor-1'])).rejects.toBe(failure);

    expect(service.sensorsSaved()).toBe(1);
  });
});
