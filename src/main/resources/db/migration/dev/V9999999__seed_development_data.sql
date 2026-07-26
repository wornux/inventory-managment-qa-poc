create temporary table dev_user_fixture (
    ordinal integer primary key,
    username varchar(80) not null,
    email varchar(255) not null,
    oidc_subject varchar(255) not null,
    active boolean not null,
    role_code varchar(80) not null
);

insert into dev_user_fixture (ordinal, username, email, oidc_subject, active, role_code)
values
    (1, 'heidi.green', 'heidi.green@example.test', '2400f5d7-47ab-5aba-a807-529f1918b0bd', true, 'SYSTEM_ADMINISTRATOR'),
    (2, 'derek.gardner', 'derek.gardner@example.test', '4e1d3024-3806-5161-a18d-281052760572', true, 'SYSTEM_ADMINISTRATOR'),
    (3, 'steven.fuentes', 'steven.fuentes@example.test', '4d37aef7-d993-5f92-865b-fa620a443cc1', true, 'SYSTEM_ADMINISTRATOR'),
    (4, 'gary.brown', 'gary.brown@example.test', 'f98e9906-24db-58e1-a170-a4741b2c0330', true, 'SYSTEM_ADMINISTRATOR'),
    (5, 'lance.brown', 'lance.brown@example.test', 'def91c11-f7fb-5ef1-b6b2-c9560da17c29', true, 'SYSTEM_ADMINISTRATOR'),
    (6, 'angela.elliott', 'angela.elliott@example.test', 'fe8ba25e-63c9-588f-b146-09c2515bda7d', true, 'INVENTORY_MANAGER'),
    (7, 'jeffrey.jones', 'jeffrey.jones@example.test', 'eb12d596-a3a2-5b5d-b873-f7c90cabe2ee', true, 'INVENTORY_MANAGER'),
    (8, 'joyce.lawrence', 'joyce.lawrence@example.test', 'defaae91-e3d3-5e97-b249-7f070927687c', true, 'INVENTORY_MANAGER'),
    (9, 'donna.cardenas', 'donna.cardenas@example.test', 'dceea017-a6c9-5016-b687-ca3b80235644', true, 'INVENTORY_MANAGER'),
    (10, 'cynthia.reynolds', 'cynthia.reynolds@example.test', '02e8f997-cece-5a1b-bac0-bdcfd4fec2f7', true, 'INVENTORY_MANAGER'),
    (11, 'michelle.valentine', 'michelle.valentine@example.test', '1158c95c-55f1-5be9-85db-58be7a83d3a0', true, 'INVENTORY_MANAGER'),
    (12, 'jessica.jacobson', 'jessica.jacobson@example.test', '2b5bb256-30f1-5d91-a4ce-44cac4362744', true, 'INVENTORY_MANAGER'),
    (13, 'ronald.martinez', 'ronald.martinez@example.test', '828df5ef-e416-50e3-a593-96a11e0036bf', true, 'INVENTORY_MANAGER'),
    (14, 'adam.caldwell', 'adam.caldwell@example.test', 'e64a9bba-7468-52ee-b0c4-4ecf937f23fe', true, 'INVENTORY_MANAGER'),
    (15, 'danielle.zuniga', 'danielle.zuniga@example.test', '6049e266-82a3-5cfa-bcb5-9827076f0ad7', true, 'INVENTORY_MANAGER'),
    (16, 'william.anderson', 'william.anderson@example.test', 'bf2122bd-a38f-531f-baa6-04798da8d744', true, 'INVENTORY_MANAGER'),
    (17, 'tabitha.meyer', 'tabitha.meyer@example.test', 'a3886bd5-cb85-5a71-9ad9-cd8a8bbd7454', true, 'INVENTORY_MANAGER'),
    (18, 'sean.thomas', 'sean.thomas@example.test', 'e2ccf135-0ab6-5bb3-be95-11f1b0835c00', true, 'INVENTORY_MANAGER'),
    (19, 'laura.carter', 'laura.carter@example.test', '34cb41b1-91f6-57a2-9d8c-43f1ca0ab7b2', true, 'INVENTORY_MANAGER'),
    (20, 'christina.meyer', 'christina.meyer@example.test', '8216eeb3-a6ed-5d09-b47f-330b86c1bc39', true, 'INVENTORY_MANAGER'),
    (21, 'blake.schwartz', 'blake.schwartz@example.test', '87448dc4-58f0-5a88-8d53-c8d3a90192a7', true, 'INVENTORY_MANAGER'),
    (22, 'jennifer.stone', 'jennifer.stone@example.test', '04b16ae3-2697-5103-ac4e-8e8879244fd5', true, 'INVENTORY_MANAGER'),
    (23, 'jeremy.garrett', 'jeremy.garrett@example.test', '080861c9-2990-5770-884f-7f60905ea27b', true, 'INVENTORY_MANAGER'),
    (24, 'allison.alexander', 'allison.alexander@example.test', 'e1487977-5ab3-51a3-a909-48c11c10c58e', true, 'INVENTORY_MANAGER'),
    (25, 'thomas.thomas', 'thomas.thomas@example.test', '2f003962-43f4-521f-8f96-8d9b1fb27725', true, 'INVENTORY_MANAGER'),
    (26, 'sara.palmer', 'sara.palmer@example.test', '95bde96a-9e09-5002-9927-d3117cc4e647', true, 'WAREHOUSE_OPERATOR'),
    (27, 'lindsay.conley', 'lindsay.conley@example.test', '91e019d9-36f4-54b5-92bc-7de4bedff5d6', true, 'WAREHOUSE_OPERATOR'),
    (28, 'joshua.mitchell', 'joshua.mitchell@example.test', 'db1103e7-b091-5925-94d3-989ca9e05134', true, 'WAREHOUSE_OPERATOR'),
    (29, 'roger.mcintyre', 'roger.mcintyre@example.test', 'a4f3f274-8926-5e58-a11f-cb4713e563a8', true, 'WAREHOUSE_OPERATOR'),
    (30, 'mario.guerrero', 'mario.guerrero@example.test', '0d343167-36a3-53f3-9a9d-4a85e3a6b856', true, 'WAREHOUSE_OPERATOR'),
    (31, 'kimberly.brown', 'kimberly.brown@example.test', '6a3c3f32-3ec4-52e1-bca8-1609f0e11b88', true, 'WAREHOUSE_OPERATOR'),
    (32, 'michelle.collins', 'michelle.collins@example.test', '3606233c-ccac-52bb-ab8c-8a23419c6d81', true, 'WAREHOUSE_OPERATOR'),
    (33, 'annette.copeland', 'annette.copeland@example.test', 'bbb0bcc3-b6fa-51db-a10a-75df8dc400cc', true, 'WAREHOUSE_OPERATOR'),
    (34, 'diana.kennedy', 'diana.kennedy@example.test', '5643e4eb-9fa9-56fc-b886-cc56006f732e', true, 'WAREHOUSE_OPERATOR'),
    (35, 'steven.brown', 'steven.brown@example.test', '7fee3bc2-2c99-5387-8f82-eab70a7a1101', true, 'WAREHOUSE_OPERATOR'),
    (36, 'cody.marshall', 'cody.marshall@example.test', '189d4b44-5cd5-5359-98df-ac29f90e56a6', true, 'WAREHOUSE_OPERATOR'),
    (37, 'ryan.proctor', 'ryan.proctor@example.test', 'bf31d5f2-ec2b-55dc-b888-5d30020adb5b', true, 'WAREHOUSE_OPERATOR'),
    (38, 'tonya.hawkins', 'tonya.hawkins@example.test', 'b016381a-e203-53d1-81aa-72eb8e3eff12', true, 'WAREHOUSE_OPERATOR'),
    (39, 'megan.alvarez', 'megan.alvarez@example.test', '2287109a-a2d5-5396-95d6-e7e32eea1b76', true, 'WAREHOUSE_OPERATOR'),
    (40, 'kayla.davis', 'kayla.davis@example.test', 'df973528-9f68-5bcf-8f29-0407e228af0f', true, 'WAREHOUSE_OPERATOR'),
    (41, 'amber.rodriguez', 'amber.rodriguez@example.test', 'c57b0871-4458-55e3-aa92-42c292ba1787', true, 'WAREHOUSE_OPERATOR'),
    (42, 'david.nichols', 'david.nichols@example.test', '2038203a-2695-5276-ad5b-7181f7e0d8c3', true, 'WAREHOUSE_OPERATOR'),
    (43, 'emily.wallace', 'emily.wallace@example.test', '41e6eed1-32c7-5cf5-8103-e67590b40d22', true, 'WAREHOUSE_OPERATOR'),
    (44, 'jay.fields', 'jay.fields@example.test', '8c301187-e11a-5256-9859-b016daa0ba22', true, 'WAREHOUSE_OPERATOR'),
    (45, 'cassandra.collins', 'cassandra.collins@example.test', 'efbb8cf0-d57e-5ede-9c99-9f4e4e68031d', true, 'WAREHOUSE_OPERATOR'),
    (46, 'charlotte.mccullough', 'charlotte.mccullough@example.test', 'e8a1ae39-f2a8-5b18-bd29-3901cacac4dc', true, 'WAREHOUSE_OPERATOR'),
    (47, 'andrew.walker', 'andrew.walker@example.test', 'dad2b4ba-f588-5bc6-bedf-16b8f463b3b2', true, 'WAREHOUSE_OPERATOR'),
    (48, 'rebekah.coleman', 'rebekah.coleman@example.test', '27d92c29-74a5-5361-96fa-54cee7e4700b', true, 'WAREHOUSE_OPERATOR'),
    (49, 'keith.washington', 'keith.washington@example.test', 'b4da4fef-1104-5e97-bba1-d804d4a1edcc', true, 'WAREHOUSE_OPERATOR'),
    (50, 'sheri.burgess', 'sheri.burgess@example.test', '3a52af06-df0f-57c8-9b6b-2bf049a2818b', true, 'WAREHOUSE_OPERATOR'),
    (51, 'jonathan.white', 'jonathan.white@example.test', '1c37f019-0a66-5add-9a9a-b64028e1b52e', true, 'WAREHOUSE_OPERATOR'),
    (52, 'david.castillo', 'david.castillo@example.test', '2e883400-0a88-53f6-a8e9-19c7d6af3780', true, 'WAREHOUSE_OPERATOR'),
    (53, 'wanda.davis', 'wanda.davis@example.test', 'ae60d6c0-9630-5ca6-a80e-43b3f3114e97', true, 'WAREHOUSE_OPERATOR'),
    (54, 'tyrone.salinas', 'tyrone.salinas@example.test', 'b5fc226e-1ae3-552a-afa9-dc52e89f6fe4', true, 'WAREHOUSE_OPERATOR'),
    (55, 'alyssa.nelson', 'alyssa.nelson@example.test', '4d066cb4-d942-5a30-a304-0aa5eb36eb58', true, 'WAREHOUSE_OPERATOR'),
    (56, 'melissa.payne', 'melissa.payne@example.test', '157d3c52-906d-51b0-bbf7-4b345dc71db2', true, 'WAREHOUSE_OPERATOR'),
    (57, 'david.dawson', 'david.dawson@example.test', '0fe27afc-116c-57d3-b7e4-d0d29fceb833', true, 'WAREHOUSE_OPERATOR'),
    (58, 'martin.trujillo', 'martin.trujillo@example.test', '0c34e02a-bfac-5ca0-9c56-e80045d41ace', true, 'WAREHOUSE_OPERATOR'),
    (59, 'katelyn.mitchell', 'katelyn.mitchell@example.test', 'c5d25c6c-35c1-5849-9808-41f1ebc658e1', true, 'WAREHOUSE_OPERATOR'),
    (60, 'paul.jacobs', 'paul.jacobs@example.test', '48447efc-3701-561e-99d9-d8a60c2050c2', true, 'WAREHOUSE_OPERATOR'),
    (61, 'helen.silva', 'helen.silva@example.test', 'e7d010e9-0ad5-5054-9bd5-257c35d49b22', true, 'INVENTORY_VIEWER'),
    (62, 'kristen.fuller', 'kristen.fuller@example.test', 'd8e0cf31-bcd7-5199-a68e-f681c893b947', true, 'INVENTORY_VIEWER'),
    (63, 'rebecca.warren', 'rebecca.warren@example.test', '2063e470-8c78-5d9d-9b2a-18de7fb83293', true, 'INVENTORY_VIEWER'),
    (64, 'kevin.hernandez', 'kevin.hernandez@example.test', '6586ea21-5a27-5608-986d-bc018aa5b48d', true, 'INVENTORY_VIEWER'),
    (65, 'daniel.morse', 'daniel.morse@example.test', '02615652-af42-5946-a8e0-e04a11939582', true, 'INVENTORY_VIEWER'),
    (66, 'michelle.burgess', 'michelle.burgess@example.test', '3681d14b-798e-5926-a110-41869fee5bf0', true, 'INVENTORY_VIEWER'),
    (67, 'yvonne.webb', 'yvonne.webb@example.test', 'ffc2a235-8ef9-52a3-8c6b-2d4598328081', true, 'INVENTORY_VIEWER'),
    (68, 'william.williams', 'william.williams@example.test', '60ce4036-cd32-58ce-90b2-d71a70ee96a2', true, 'INVENTORY_VIEWER'),
    (69, 'andrew.sanford', 'andrew.sanford@example.test', '75683b21-5116-5dae-a54d-dd25e14b9bde', true, 'INVENTORY_VIEWER'),
    (70, 'karen.holland', 'karen.holland@example.test', '3be51bc1-958f-5a42-bc20-e44e82f7f3fc', true, 'INVENTORY_VIEWER'),
    (71, 'franklin.schroeder', 'franklin.schroeder@example.test', 'a8655e5e-2fba-51bc-864b-e6b615172e5e', true, 'INVENTORY_VIEWER'),
    (72, 'paige.ortiz', 'paige.ortiz@example.test', '65403c89-49ae-5b4c-a582-44c04bcde439', true, 'INVENTORY_VIEWER'),
    (73, 'susan.buchanan', 'susan.buchanan@example.test', '8802fb37-f85d-56d1-96e4-356b90be4431', true, 'INVENTORY_VIEWER'),
    (74, 'john.hamilton', 'john.hamilton@example.test', '3482e07d-1e16-58ae-ba4e-aa4af40ae6dc', true, 'INVENTORY_VIEWER'),
    (75, 'andrea.fowler', 'andrea.fowler@example.test', '36b53ebe-5508-5bde-bcdf-876f472e50a8', true, 'INVENTORY_VIEWER'),
    (76, 'christopher.calderon', 'christopher.calderon@example.test', 'c0c7cfb2-e835-5f52-8fad-7d3f0d39ab4a', true, 'INVENTORY_VIEWER'),
    (77, 'william.clark', 'william.clark@example.test', 'ccdf6fac-a064-5ba8-92bf-ab4c0d546133', true, 'INVENTORY_VIEWER'),
    (78, 'joseph.moore', 'joseph.moore@example.test', '1471a822-880a-5fe9-90e6-2af148752912', true, 'INVENTORY_VIEWER'),
    (79, 'susan.henderson', 'susan.henderson@example.test', '0da3016f-557b-56a6-888b-eb2f57d88fab', true, 'INVENTORY_VIEWER'),
    (80, 'courtney.calderon', 'courtney.calderon@example.test', 'cac7b4c8-1f99-56f0-a80f-8b1ef5facc8f', true, 'INVENTORY_VIEWER'),
    (81, 'bethany.boone', 'bethany.boone@example.test', '65b273a6-6fa5-503e-900f-ac2b4674ec9f', true, 'INVENTORY_VIEWER'),
    (82, 'stephanie.lara', 'stephanie.lara@example.test', 'e2500eb0-e80d-54be-ab01-28d74513a334', true, 'INVENTORY_VIEWER'),
    (83, 'teresa.stevenson', 'teresa.stevenson@example.test', 'd84bf063-bb2f-5bf1-9fdb-0110084fdb7c', true, 'INVENTORY_VIEWER'),
    (84, 'william.arroyo', 'william.arroyo@example.test', 'a8ce851f-4a19-5d53-9ca8-50a4be9c0c75', true, 'INVENTORY_VIEWER'),
    (85, 'derek.webster', 'derek.webster@example.test', '33e0df00-4139-5a4b-82fc-5ae1e09a3d0e', true, 'INVENTORY_VIEWER'),
    (86, 'darren.vaughn', 'darren.vaughn@example.test', 'a07482b7-a16f-56b1-96da-b438c13dcf45', true, 'INVENTORY_VIEWER'),
    (87, 'cody.ortiz', 'cody.ortiz@example.test', '234602ad-70cf-5b77-9b9d-ce4ef80bb22f', true, 'INVENTORY_VIEWER'),
    (88, 'lisa.johnson', 'lisa.johnson@example.test', '4d6e94bd-925f-5e0d-abfb-b33ac431275d', true, 'INVENTORY_VIEWER'),
    (89, 'sandra.cruz', 'sandra.cruz@example.test', '06961289-6d46-53f7-8687-e95c49728cf9', true, 'INVENTORY_VIEWER'),
    (90, 'andrew.garcia', 'andrew.garcia@example.test', 'f3ba98c9-8f0c-5cb1-a45a-0f4374ef15da', true, 'INVENTORY_VIEWER'),
    (91, 'amanda.harris', 'amanda.harris@example.test', 'ed62440d-44bf-512d-b5a7-22d52f958268', true, 'INVENTORY_VIEWER'),
    (92, 'john.brock', 'john.brock@example.test', '542e55cc-67b5-5d21-b06c-52e9d3a5a68e', true, 'INVENTORY_VIEWER'),
    (93, 'jacob.mcgee', 'jacob.mcgee@example.test', '19cddd43-d239-5975-939b-759fd3471fac', false, 'INVENTORY_VIEWER'),
    (94, 'jeremiah.hughes', 'jeremiah.hughes@example.test', '0c7bb855-4421-5b4e-8a95-45a987966892', false, 'INVENTORY_VIEWER'),
    (95, 'amber.chang', 'amber.chang@example.test', 'ce993775-8a1d-5b48-a720-fca99fdc0399', false, 'INVENTORY_VIEWER'),
    (96, 'steven.faulkner', 'steven.faulkner@example.test', '31f12750-2df4-59e9-979b-025608f80f75', false, 'INVENTORY_VIEWER'),
    (97, 'lacey.coleman', 'lacey.coleman@example.test', 'b4ffaa27-0d5b-5a31-8c7a-78e5e394128f', false, 'INVENTORY_VIEWER'),
    (98, 'adrian.bentley', 'adrian.bentley@example.test', 'd12c0db3-8a95-507e-b981-7df05cf22064', false, 'INVENTORY_VIEWER'),
    (99, 'jessica.daniels', 'jessica.daniels@example.test', '1dd96068-e4d7-5ac3-a341-38ff84d495ff', false, 'INVENTORY_VIEWER'),
    (100, 'ryan.anderson', 'ryan.anderson@example.test', 'a4091a0b-1570-5dc7-91f1-b6d73425ea25', false, 'INVENTORY_VIEWER');

insert into app_user (
    username,
    email,
    oidc_issuer,
    oidc_subject,
    active,
    created_by,
    created_date,
    last_modified_by,
    last_modified_date
)
select
    username,
    email,
    'http://localhost:7777/realms/wornux',
    oidc_subject,
    active,
    'dev-seed',
    timestamptz '2025-01-01 09:00:00+00' + make_interval(days => ordinal % 180),
    'dev-seed',
    timestamptz '2025-01-01 09:00:00+00' + make_interval(days => ordinal % 180)
from dev_user_fixture;

insert into user_role (user_id, role_id, assigned_at)
select
    app_user.id,
    role.id,
    app_user.created_date
from dev_user_fixture
join app_user using (username)
join role on role.code = dev_user_fixture.role_code;

insert into category (name, description)
values
    ('Electronics', 'Electronic equipment and components.'),
    ('Computers & Accessories', 'Computers, peripherals, and workstation accessories.'),
    ('Office Supplies', 'Everyday workplace consumables and stationery.'),
    ('Tools & Hardware', 'Hand tools, power tools, and general hardware.'),
    ('Safety Equipment', 'Personal protective and workplace safety equipment.'),
    ('Warehouse Equipment', 'Material handling and warehouse operations equipment.'),
    ('Cleaning Supplies', 'Commercial cleaning products and equipment.'),
    ('Packaging Materials', 'Boxes, protective packaging, labels, and tape.'),
    ('Furniture', 'Commercial office and warehouse furniture.'),
    ('Lighting', 'Indoor, outdoor, and task lighting products.'),
    ('Electrical', 'Electrical installation supplies and accessories.'),
    ('Plumbing', 'Commercial plumbing parts and maintenance supplies.'),
    ('Automotive', 'Vehicle maintenance tools, parts, and consumables.'),
    ('Industrial Components', 'Bearings, fasteners, belts, and machine components.'),
    ('Networking', 'Network infrastructure and connectivity equipment.'),
    ('Audio & Video', 'Presentation, conferencing, and media equipment.'),
    ('Mobile Accessories', 'Charging, protection, and mobile productivity accessories.'),
    ('Storage & Organization', 'Shelving, bins, cabinets, and organization systems.'),
    ('Food Service', 'Commercial kitchen and food-service supplies.'),
    ('Laboratory Supplies', 'General laboratory equipment and consumables.'),
    ('Outdoor Equipment', 'Grounds maintenance and outdoor work equipment.'),
    ('Textiles', 'Uniforms, protective fabrics, and workplace textiles.'),
    ('Printing Supplies', 'Printers, labels, ink, toner, and print media.'),
    ('Security Systems', 'Access control, surveillance, and facility security equipment.');

insert into supplier (
    name,
    contact_name,
    email,
    phone,
    active,
    created_by,
    created_date,
    last_modified_by,
    last_modified_date
)
select
    name,
    contact_name,
    email,
    phone,
    active,
    'dev-seed',
    timestamptz '2024-06-01 09:00:00+00' + make_interval(days => ordinal % 365),
    'dev-seed',
    timestamptz '2024-06-01 09:00:00+00' + make_interval(days => ordinal % 365)
from (values
    (1, 'Johnson, Rodriguez and Jordan', 'David Fletcher', 'orders001@example.test', '+1-202-555-1001', true),
    (2, 'Richardson-Evans', 'James Hodge', 'orders002@example.test', '+1-202-555-1002', true),
    (3, 'Hill, Morrow and Mccoy', 'Paul Santiago', 'orders003@example.test', '+1-202-555-1003', true),
    (4, 'Grant LLC', 'Ronald Williams', 'orders004@example.test', '+1-202-555-1004', true),
    (5, 'Wright LLC', 'Michele Larson', 'orders005@example.test', '+1-202-555-1005', true),
    (6, 'Adams-Rodriguez', 'Desiree Wallace', 'orders006@example.test', '+1-202-555-1006', true),
    (7, 'Byrd Inc', 'Austin Goodwin', 'orders007@example.test', '+1-202-555-1007', true),
    (8, 'Hines-Francis', 'Alicia Delgado', 'orders008@example.test', '+1-202-555-1008', true),
    (9, 'Carlson, Wolfe and Lopez', 'Abigail Snow', 'orders009@example.test', '+1-202-555-1009', true),
    (10, 'Pearson-Mcclain', 'Richard Ray', 'orders010@example.test', '+1-202-555-1010', true),
    (11, 'Buck and Sons', 'Kathleen Mcdonald', 'orders011@example.test', '+1-202-555-1011', true),
    (12, 'Cook-Smith', 'Jacob Zimmerman', 'orders012@example.test', '+1-202-555-1012', true),
    (13, 'Mayo, Webb and Sampson', 'Louis Mejia', 'orders013@example.test', '+1-202-555-1013', true),
    (14, 'Walters-Williams', 'Sarah Campbell', 'orders014@example.test', '+1-202-555-1014', true),
    (15, 'Andrews-Stevenson', 'Michael Powell', 'orders015@example.test', '+1-202-555-1015', true),
    (16, 'Mccann-Hicks', 'Melissa Harvey', 'orders016@example.test', '+1-202-555-1016', true),
    (17, 'Hodges Ltd', 'Nicholas Jones', 'orders017@example.test', '+1-202-555-1017', true),
    (18, 'Watts Inc', 'James Carroll', 'orders018@example.test', '+1-202-555-1018', true),
    (19, 'Boyd, Warner and Wright', 'Jessica Patterson', 'orders019@example.test', '+1-202-555-1019', true),
    (20, 'Wright and Sons', 'Tyler Medina', 'orders020@example.test', '+1-202-555-1020', true),
    (21, 'Smith, Hayes and Anderson', 'Heather Blair', 'orders021@example.test', '+1-202-555-1021', true),
    (22, 'Johnson Inc', 'Claire Sloan', 'orders022@example.test', '+1-202-555-1022', true),
    (23, 'Garza PLC', 'Rachel Blackburn', 'orders023@example.test', '+1-202-555-1023', true),
    (24, 'Gonzalez, Dominguez and Koch', 'Dr. Christian Dennis', 'orders024@example.test', '+1-202-555-1024', true),
    (25, 'Hill, Jimenez and Thomas', 'Lindsay Martinez', 'orders025@example.test', '+1-202-555-1025', true),
    (26, 'Smith-Pierce', 'Brenda Humphrey', 'orders026@example.test', '+1-202-555-1026', true),
    (27, 'Carson, Jones and Church', 'Ryan Elliott', 'orders027@example.test', '+1-202-555-1027', true),
    (28, 'Kaiser-Martinez', 'Duane Weaver', 'orders028@example.test', '+1-202-555-1028', true),
    (29, 'Cole Group', 'Kenneth Hernandez', 'orders029@example.test', '+1-202-555-1029', true),
    (30, 'Nelson-Baker', 'Robert Parsons', 'orders030@example.test', '+1-202-555-1030', true),
    (31, 'Glenn LLC', 'Dawn Brown', 'orders031@example.test', '+1-202-555-1031', true),
    (32, 'Mueller-Jones', 'Christy Snow', 'orders032@example.test', '+1-202-555-1032', true),
    (33, 'Diaz-Williams', 'Richard Heath', 'orders033@example.test', '+1-202-555-1033', true),
    (34, 'Burton, Vega and Crawford', 'Natasha Ruiz', 'orders034@example.test', '+1-202-555-1034', true),
    (35, 'Hall-Barnett', 'Johnny Ross', 'orders035@example.test', '+1-202-555-1035', true),
    (36, 'Thomas PLC', 'Nicholas Lopez', 'orders036@example.test', '+1-202-555-1036', true),
    (37, 'Wright-Smith', 'Jasmine Pacheco', 'orders037@example.test', '+1-202-555-1037', true),
    (38, 'Haas, Buchanan and Davis', 'Sara Pitts', 'orders038@example.test', '+1-202-555-1038', true),
    (39, 'Powers, Le and Reid', 'Glenda Hunt', 'orders039@example.test', '+1-202-555-1039', true),
    (40, 'Craig-Adams', 'Scott Cook', 'orders040@example.test', '+1-202-555-1040', true),
    (41, 'Bentley, Pierce and Ross', 'Lisa Pace', 'orders041@example.test', '+1-202-555-1041', true),
    (42, 'Robinson-Hardin', 'Cathy Smith', 'orders042@example.test', '+1-202-555-1042', true),
    (43, 'Bradley, Jones and Atkins', 'William Rogers', 'orders043@example.test', '+1-202-555-1043', true),
    (44, 'Spencer-Thompson', 'Cheryl Cole', 'orders044@example.test', '+1-202-555-1044', true),
    (45, 'Mcgrath-Rodriguez', 'Michael Ramos', 'orders045@example.test', '+1-202-555-1045', true),
    (46, 'Tyler Group', 'Brandy Faulkner', 'orders046@example.test', '+1-202-555-1046', true),
    (47, 'Frye and Sons', 'Jordan Mccoy', 'orders047@example.test', '+1-202-555-1047', true),
    (48, 'Gibbs-Luna', 'Dale Hernandez', 'orders048@example.test', '+1-202-555-1048', true),
    (49, 'Mitchell-Stewart', 'Matthew Daniels', 'orders049@example.test', '+1-202-555-1049', true),
    (50, 'King, Wagner and Morris', 'Richard Stone', 'orders050@example.test', '+1-202-555-1050', true),
    (51, 'Andrews, Alvarez and Robinson', 'Joseph Gibson', 'orders051@example.test', '+1-202-555-1051', true),
    (52, 'Blair, Sawyer and Lopez', 'Collin Pena', 'orders052@example.test', '+1-202-555-1052', true),
    (53, 'Banks-Mendoza', 'Jacqueline Frank', 'orders053@example.test', '+1-202-555-1053', true),
    (54, 'Martin-Lopez', 'Timothy Castillo', 'orders054@example.test', '+1-202-555-1054', true),
    (55, 'Martinez-Davis', 'Gary Bennett', 'orders055@example.test', '+1-202-555-1055', true),
    (56, 'Davis-Thompson', 'Charles Montoya', 'orders056@example.test', '+1-202-555-1056', true),
    (57, 'Cortez-Acosta', 'David Kirby', 'orders057@example.test', '+1-202-555-1057', true),
    (58, 'Johnson and Sons', 'Danny Hogan', 'orders058@example.test', '+1-202-555-1058', true),
    (59, 'Edwards, Miller and Perry', 'Wendy Tucker', 'orders059@example.test', '+1-202-555-1059', true),
    (60, 'Jimenez-Hall', 'Stephanie Wong', 'orders060@example.test', '+1-202-555-1060', true),
    (61, 'Cook Inc', 'Jeffrey Maldonado', 'orders061@example.test', '+1-202-555-1061', true),
    (62, 'Blackburn LLC', 'Sean Thompson', 'orders062@example.test', '+1-202-555-1062', true),
    (63, 'Neal, Cohen and Henderson', 'Mr. Michael Cooper', 'orders063@example.test', '+1-202-555-1063', true),
    (64, 'Nelson, Cain and Snow', 'Bailey Blake', 'orders064@example.test', '+1-202-555-1064', true),
    (65, 'Gilbert PLC', 'Nathan Thompson', 'orders065@example.test', '+1-202-555-1065', true),
    (66, 'Adams PLC', 'Lauren Gardner', 'orders066@example.test', '+1-202-555-1066', true),
    (67, 'Chavez Ltd', 'Mrs. Michele Bush', 'orders067@example.test', '+1-202-555-1067', true),
    (68, 'Fitzpatrick-Wright', 'Heather Long', 'orders068@example.test', '+1-202-555-1068', true),
    (69, 'Grant Inc', 'Mary Barber', 'orders069@example.test', '+1-202-555-1069', true),
    (70, 'Sanders-Poole', 'Cynthia Miller', 'orders070@example.test', '+1-202-555-1070', true),
    (71, 'Parker-Zuniga', 'Alec Spencer', 'orders071@example.test', '+1-202-555-1071', true),
    (72, 'Ramirez Group', 'Richard Blair', 'orders072@example.test', '+1-202-555-1072', true),
    (73, 'Hughes-Rose', 'Lori Paul', 'orders073@example.test', '+1-202-555-1073', true),
    (74, 'Davis Inc', 'Tracy Sutton', 'orders074@example.test', '+1-202-555-1074', true),
    (75, 'Allen-Hawkins', 'Matthew Simmons', 'orders075@example.test', '+1-202-555-1075', true),
    (76, 'Wilkinson-Powell', 'Katherine Huffman', 'orders076@example.test', '+1-202-555-1076', true),
    (77, 'Wilson, Wiggins and Mccullough', 'Matthew Warren', 'orders077@example.test', '+1-202-555-1077', true),
    (78, 'Martin, Miranda and Martinez', 'Sandra Villanueva', 'orders078@example.test', '+1-202-555-1078', true),
    (79, 'Bryant, Paul and Chandler', 'Randy Cooke', 'orders079@example.test', '+1-202-555-1079', true),
    (80, 'Martin, Adams and Potter', 'Lisa Meyers', 'orders080@example.test', '+1-202-555-1080', true),
    (81, 'Beck Inc', 'Amanda Sherman', 'orders081@example.test', '+1-202-555-1081', true),
    (82, 'Lam-Lopez', 'Emily Lester', 'orders082@example.test', '+1-202-555-1082', true),
    (83, 'Adams Group', 'Patrick Jackson', 'orders083@example.test', '+1-202-555-1083', true),
    (84, 'Duncan Inc', 'Jennifer Johnson', 'orders084@example.test', '+1-202-555-1084', true),
    (85, 'Jones, Lambert and Skinner', 'Joel Solomon', 'orders085@example.test', '+1-202-555-1085', true),
    (86, 'Simmons, Ray and Smith', 'Cheryl Baker', 'orders086@example.test', '+1-202-555-1086', true),
    (87, 'Allen, Sweeney and Lopez', 'Matthew Garrett', 'orders087@example.test', '+1-202-555-1087', true),
    (88, 'Barron, Harris and Braun', 'Carol Guzman', 'orders088@example.test', '+1-202-555-1088', true),
    (89, 'Cruz-Duncan', 'Sherry Smith', 'orders089@example.test', '+1-202-555-1089', true),
    (90, 'Luna LLC', 'Teresa Graham', 'orders090@example.test', '+1-202-555-1090', true),
    (91, 'Morris-Villa', 'Patricia Clark', 'orders091@example.test', '+1-202-555-1091', true),
    (92, 'Ross Ltd', 'Andrea Torres', 'orders092@example.test', '+1-202-555-1092', true),
    (93, 'Turner, Wright and Floyd', 'Christopher Bradley', 'orders093@example.test', '+1-202-555-1093', true),
    (94, 'Ramos PLC', 'Robert Hodges', 'orders094@example.test', '+1-202-555-1094', true),
    (95, 'Ellis-Silva', 'Robert Alvarez', 'orders095@example.test', '+1-202-555-1095', true),
    (96, 'Moore-Dyer', 'Jeffrey Tucker', 'orders096@example.test', '+1-202-555-1096', false),
    (97, 'Harvey-Edwards', 'Andrew Carter', 'orders097@example.test', '+1-202-555-1097', false),
    (98, 'Dean and Sons', 'Tiffany Hamilton', 'orders098@example.test', '+1-202-555-1098', false),
    (99, 'Chapman-Waters', 'Amanda Hatfield', 'orders099@example.test', '+1-202-555-1099', false),
    (100, 'Mclaughlin, Craig and Baker', 'Peter Parker', 'orders100@example.test', '+1-202-555-1100', false)
) as fixture(ordinal, name, contact_name, email, phone, active);

with product_fixture as (
    select
        fixture.ordinal,
        fixture.brands[1 + ((fixture.ordinal - 1) % cardinality(fixture.brands))] as brand,
        fixture.items[1 + (((fixture.ordinal - 1) / cardinality(fixture.brands)) % cardinality(fixture.items))] as item
    from (
        select
            ordinal,
            array['Apex', 'Beacon', 'Cobalt', 'Delta', 'Evergreen', 'Forge', 'Harbor', 'Ion', 'Juniper', 'Keystone']::text[] as brands,
            array[
                'Cordless Drill', 'Safety Helmet', 'Storage Bin', 'Network Switch', 'Task Light',
                'Label Printer', 'Office Chair', 'Tool Cabinet', 'Power Strip', 'Hand Truck',
                'Barcode Scanner', 'Air Filter', 'Work Gloves', 'Packing Tape', 'Cable Organizer',
                'Monitor Stand', 'First Aid Kit', 'Water Pump', 'Socket Set', 'Cleaning Cart'
            ]::text[] as items
        from generate_series(1, 500) as series(ordinal)
    ) fixture
),
ranked_categories as (
    select id, row_number() over (order by id) as ordinal from category
),
ranked_suppliers as (
    select id, row_number() over (order by id) as ordinal from supplier
)
insert into product (
    sku,
    name,
    description,
    unit_price,
    quantity_on_hand,
    minimum_stock,
    active,
    category_id,
    supplier_id,
    created_by,
    created_date,
    last_modified_by,
    last_modified_date
)
select
    format('WRX-%s', lpad(product_fixture.ordinal::text, 6, '0')),
    format('%s %s %s', product_fixture.brand, product_fixture.item, lpad(product_fixture.ordinal::text, 3, '0')),
    format('Commercial-grade %s supplied for inventory and facility operations.', lower(product_fixture.item)),
    (((product_fixture.ordinal * 137) % 250000 + 500)::numeric / 100)::numeric(12, 2),
    case
        when product_fixture.ordinal % 10 = 0 then 3 + (product_fixture.ordinal % 5)
        else 50 + (product_fixture.ordinal % 150)
            + 20 + (product_fixture.ordinal % 40)
            - 5 - (product_fixture.ordinal % 15)
            - 1 - (product_fixture.ordinal % 3)
    end,
    5 + (product_fixture.ordinal % 20),
    product_fixture.ordinal % 25 <> 0,
    ranked_categories.id,
    ranked_suppliers.id,
    'dev-seed',
    timestamptz '2024-01-01 09:00:00+00' + make_interval(days => product_fixture.ordinal % 365),
    'dev-seed',
    timestamptz '2025-01-01 09:00:00+00' + make_interval(days => product_fixture.ordinal % 180)
from product_fixture
join ranked_categories
    on ranked_categories.ordinal = 1 + ((product_fixture.ordinal - 1) % 24)
join ranked_suppliers
    on ranked_suppliers.ordinal = 1 + ((product_fixture.ordinal - 1) % 100);

with ranked_products as (
    select id, row_number() over (order by sku)::integer as ordinal from product
),
ranked_users as (
    select id, username, row_number() over (order by username)::integer as ordinal from app_user
)
insert into stock_movement (
    product_id,
    user_id,
    movement_type,
    quantity_delta,
    reason,
    created_by,
    created_date,
    last_modified_by,
    last_modified_date
)
select
    ranked_products.id,
    ranked_users.id,
    movement.movement_type,
    movement.quantity_delta,
    movement.reason,
    ranked_users.username,
    timestamptz '2025-01-01 08:00:00+00'
        + make_interval(days => (ranked_products.ordinal % 300) + movement.day_offset),
    ranked_users.username,
    timestamptz '2025-01-01 08:00:00+00'
        + make_interval(days => (ranked_products.ordinal % 300) + movement.day_offset)
from ranked_products
join ranked_users on ranked_users.ordinal = 1 + ((ranked_products.ordinal - 1) % 100)
cross join lateral (values
    ('INITIAL_STOCK', 50 + (ranked_products.ordinal % 150), 'Opening inventory balance', 0),
    ('PURCHASE', 20 + (ranked_products.ordinal % 40), 'Purchase order received', 30),
    (
        'SALE',
        case
            when ranked_products.ordinal % 10 = 0 then -(
                50 + (ranked_products.ordinal % 150)
                + 20 + (ranked_products.ordinal % 40)
                - 1 - (ranked_products.ordinal % 3)
                - 3 - (ranked_products.ordinal % 5)
            )
            else -(5 + (ranked_products.ordinal % 15))
        end,
        'Customer order fulfilled',
        60
    ),
    ('DAMAGED', -(1 + (ranked_products.ordinal % 3)), 'Damaged during handling', 90)
) as movement(movement_type, quantity_delta, reason, day_offset);

drop table dev_user_fixture;
