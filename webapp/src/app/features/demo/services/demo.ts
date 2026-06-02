import { Injectable} from '@angular/core';
import { httpResource} from '@angular/common/http';
import {ReadSimpleMetric} from '../../../core/models/read-simple-metric';

@Injectable({
  providedIn: 'root',
})
export default class Demo {

  public metricsResource = httpResource<ReadSimpleMetric[]>(() => '/api/demo/metrics?limit=100');

  public getMetricByTime(time: string): ReadSimpleMetric | undefined {
    return this.metricsResource.value()?.find(m => m.timestamp === time);
  }
// TODO: Move to core and autogenerate from OpenAPI
}
