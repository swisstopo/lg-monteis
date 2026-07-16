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
pnpm run test
```

## Running End-to-End (E2E) Tests

Run tests with a visible browser:

```bash
pnpm run e2e:headed
```

Run tests in headless mode:

```bash
pnpm run e2e:headless
```

Open the latest test report:

```bash
pnpm run e2e:show-report
```

To generate a Playwright trace for debugging, run:

```bash
pnpm run e2e:headed --retries=1
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## Project structure

```
src/
├── app/                            <-- Angular bootstrap (app.config.ts)
│
├── config/                         <-- Application composition and framework integration
│   ├── routes.config.ts
│   ├── workbench.config.ts
│   └── icon-provider.ts
│
├── core/                           <-- Application-wide infrastructure and generated artifacts
│   ├── api/                        <-- OpenAPI generated clients
│   ├── models/                     <-- Shared business/API models
│   ├── interceptors/               <-- HTTP interceptors
│   └── auth/                       <-- Authentication infrastructure
│
├── features/                       <-- Business features and pages
│   └── overview/
│       ├── overview.routes.ts
│       ├── overview/
│       ├── metrics-menu/
│       └── ag-grid-record/
│
├── ui/                             <-- Reusable UI building blocks and wrappers
│   ├── table/                      <-- AG Grid wrapper
│   ├── route-button/               <-- Workbench navigation button
│   └── ...
│
└── shared/                         <-- Generic utilities and cross-cutting helpers
    ├── utils/
    ├── constants/
    └── types/
```

## Code Generation

This Project uses code generation for generating API services and DTOs. In order for the project to work you need to execute the code generation. This can be done by executing the following.

```shell
pnpm generate-api
```
