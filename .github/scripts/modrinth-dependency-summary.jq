if (.dependencies | type) == "array" then
  .dependencies as $dependencies
  | {
      dependency_count: ($dependencies | length),
      dependencies: [
        $dependencies[]
        | if type == "object" then
            {
              dependency_type: (if has("dependency_type") then .dependency_type else null end),
              project_id: (if has("project_id") then .project_id else null end),
              version_id: (if has("version_id") then .version_id else null end),
              file_name_present: has("file_name"),
              file_name: (if has("file_name") then .file_name else null end)
            }
          else
            {
              dependency_type: null,
              project_id: null,
              version_id: null,
              file_name_present: false,
              file_name: null
            }
          end
      ]
    }
else
  {dependency_count: null, dependencies: []}
end
