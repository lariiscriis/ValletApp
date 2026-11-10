<div align="center">
   <img height="200" alt="VALLET_logo" src="https://github.com/user-attachments/assets/c016879c-235f-423f-b553-08cb60cf02e3" />
   
   <a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=26&pause=10000&color=3b5042&width=600&height=55&lines=Sistema+de+Gest%C3%A3o+de+Estacionamentos" alt="Typing SVG" /></a>
</div>

<p>
Seja bem-vindo ao projeto <b>Vallet</b>, um aplicativo Android e plataforma web, seguindo a arquitetura MVVM (Model-View-ViewModel) e com integração total ao Firebase.
Ele busca otimizar o processo de <b>reserva, monitoramento e administração de vagas de estacionamento,</b> beneficiando tanto motoristas quanto os administradores de estacionamentos.
</p>

>Este repositório inclui o app completo para motoristas reservarem vagas.

<h3>Verifique outras partes do nosso projeto</h3>

 | Módulo / Repositório | Descrição | Link de Acesso |
| :--- | :--- | :--- |
| **ValletWeb** (Gestores) | Dashboards e controle de ocupação para gestores de estacionamento. | [Repositório Web](https://github.com/Pereira-Sah/ValletWeb) |
| **ValletApp** (Android) | Aplicativo móvel voltado para motoristas e usuários finais. | [Repositório Android](https://github.com/lariiscriis/ValletProjeto) |
| **ValletAPI** | API backend para conexão e gerenciamento de todo o ecossistema. | [Repositório API](https://github.com/Almile/ValletAPI) |
| **ValletIOT** | Sistema de leitura de placas (LPR) com uso de webcam. | [Repositório IOT](https://github.com/Almile/ValletIOT) |
| **ValletDesktop** | Sistema desktop voltado para os administradores gerais. | [Repositório Desktop](https://github.com/Almile/valletDesktop) |

<br><br>
<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-637365?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Android-3b5042?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Firebase-cbd9c5?style=for-the-badge&logo=firebase&logoColor=white" />
  <img src="https://img.shields.io/badge/Firestore-637365?style=for-the-badge&logo=google-cloud&logoColor=white" />
  <img src="https://img.shields.io/badge/MVVM-3b5042?style=for-the-badge&logo=architecture&logoColor=white" />
  <img src="https://img.shields.io/badge/Glide-cbd9c5?style=for-the-badge&logo=google&logoColor=white" />
  <img src="https://img.shields.io/badge/RecyclerView-637365?style=for-the-badge&logo=android&logoColor=white" />
</p>
<br>

## ✨ Funcionalidades

### 👤 Motoristas
-  Cadastro e login com Firebase Auth
-  Reserva de vagas com limite de tempo
-  Histórico de reservas
-  Dashboard com total de reservas e tempo reservado
-  Edição de perfil com foto, CNH, e mais

### 🧑‍💼 Administradores
-  Cadastro de estacionamentos
-  Criação de vagas
-  Monitoramento de reservas em tempo real
-  Relatórios e visualização de dados


## Arquitetura

O projeto segue a arquitetura **MVVM (Model-View-ViewModel)**, com separação clara de responsabilidades e pastas:
```
📦 valletprojeto
├── adapter/
├── dao/
├── model/
├── viewmodel/
└── fragments/
```

## 🔥 Como Executar

1. Clone este repositório:
   ```bash
   git clone https://github.com/lariiscriis/ValletProjeto.git



