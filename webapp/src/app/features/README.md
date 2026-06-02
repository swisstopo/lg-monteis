# Purpose

This folder contains Feature folders which themselves contain multiple components and the additional services.

# Example structure
```
features/
      auth/
        auth.routes.ts                      <-- Helps split routing into clean structure
        component_1/                        <-- First component of the feature
          component_1.component.ts
          component_1.component.scss
          component_1.component.html
          component_1.component.spec.ts
        component_2/                        <-- Second component of the feature
        models/                             <-- Models that are only internal of this specific feature
        validators/                         <-- Validators that are only used within this feature
      dashboard/
```
