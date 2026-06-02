# Purpose
This folder contains highly reusable and granular UI components, such as buttons, toggles, pipes, dropdowns, etc.

```
shared/
    validators/                  <-- Validators that can be reused
    components/
      dropdown/
        dropdown.component.ts      <-- The actual component logic
        dropdown.component.html    <-- The template
        dropdown.component.scss    <-- The styles
      data-table/
        data-table.component.ts
        data-table.component.html
        data-table.component.scss
    types/
      dropdown-option.type.ts      <-- Just the TypeScript interface
      table-column.type.ts
    pipes/
      format-date.pipe.ts
```
