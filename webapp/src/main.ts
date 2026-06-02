import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import {AllCommunityModule, ModuleRegistry} from 'ag-grid-community';

ModuleRegistry.registerModules([ AllCommunityModule ]);
bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
