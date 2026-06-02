# Webapp

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.13.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## Project structure
```
src/
  app/
    core/
      models/                       <-- Contains business/API models used throughout the app (e.g., User.ts)
      i18n/                         <-- Core services for things like language detection

    layout/
      main-layout/                  <-- The main wrapper shell (contains sidebar, header, and the <router-outlet>)
        main-layout.component.ts
        main-layout.component.html
        main-layout.component.scss
      auth-layout/                  <-- A minimal shell used only for login/register screens
        auth-layout.component.ts
        auth-layout.component.html
        auth-layout.component.scss
      header/                       <-- Structural UI pieces used specifically to build the layouts
        header.component.ts
        header.component.html
        header.component.scss

    shared/
      validators/                   <-- Validators that can be reused anywhere (e.g., strong-password.validator.ts)
      components/
        dropdown/
          dropdown.component.ts     <-- The actual component logic
          dropdown.component.html   <-- The template
          dropdown.component.scss   <-- The styles
        data-table/
          data-table.component.ts
          data-table.component.html
          data-table.component.scss
      types/
        dropdown-option.type.ts     <-- Just the TypeScript interface for the dropdown component
        table-column.type.ts        <-- Just the TypeScript interface for the data-table component
      pipes/
        format-date.pipe.ts         <-- Reusable data transformation pipes

    features/
      auth/
        auth.routes.ts              <-- Local router linking paths to auth components
        login-form/                 <-- First component of the feature
          login-form.component.ts
          login-form.component.scss
          login-form.component.html
          login-form.component.spec.ts
        reset-password/             <-- Second component of the feature
          reset-password.component.ts
          ...
        models/                     <-- Models only used internally by the Auth feature (e.g., LoginPayload.ts)
        validators/                 <-- Validators only used within this feature
      
      dashboard/                    <-- Another fully isolated feature 
        dashboard.routes.ts
        dashboard-view/
          ...
```
