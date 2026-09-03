import { isDevMode } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { AllCommunityModule, enableDevValidations, ModuleRegistry } from 'ag-grid-community';
import { App } from './app/app';
import { appConfig } from './app/app.config';

if (isDevMode()) {
  enableDevValidations();
}
ModuleRegistry.registerModules([AllCommunityModule]);
bootstrapApplication(App, appConfig).catch((err) => console.error(err));
