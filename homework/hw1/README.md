# ДЗ 1. Доступность приложения в Kubernetes

Скринкаст (4:43): https://drive.google.com/file/d/13cnOAxfWy-Ia0zwf-CXJeZdOxCNniNsv/view?usp=sharing

| Время | Раздел |
| --- | --- |
| 0:00 | Карта ролика |
| 0:12 | Манифесты |
| 1:05 | Запуск |
| 1:13 | Сценарий 1 |
| 2:02 | Сценарий 2 |
| 2:53 | Сценарий 3 |
| 3:48 | Выводы |

## Схема

```
клиент (load-test.sh, внутри него curl), Linux
   │  http://localhost:30080/
   ▼
контейнер узла kind (hw1-control-plane), порт 30080 проброшен через extraPortMappings
   ▼
Service hw1-app: NodePort 30080 → port 80 → targetPort 8080
   │  берёт адреса только Ready-Pod из EndpointSlice
   ├───────────┬───────────┐
   ▼           ▼           ▼
 Pod 1       Pod 2       Pod 3      (метка app=hw1-app)
   ▲
Deployment → ReplicaSet → Pod
```

- Приложение (Java, Spring Boot): `GET /` отдаёт message, version и pod; `GET /live` всегда 200; `GET /ready` отвечает 200 или 503 по внутреннему флагу; `POST /unready` и `POST /ready-on` переключают флаг. Версия задаётся при сборке образа, имя Pod берётся из hostname. На SIGTERM приложение доделывает текущие запросы (
`server: shutdown: graceful`).
- Deployment держит 3 Pod, Service раздаёт запросы только на Ready-Pod из EndpointSlice.
- Путь запроса: клиент → localhost:30080 → узел kind → Service → один из Pod → порт 8080. `kubectl port-forward` не использовал.

## Сборка и запуск с чистого кластера

Из папки `homework/hw1`, Docker Desktop уже запущен:

```bash
kind delete cluster --name hw1          # если кластер уже был

docker build --build-arg VERSION=v1 -t hw1-app:v1 ./app
docker build --build-arg VERSION=v2 -t hw1-app:v2 ./app

kind create cluster --config kubernetes/kind-config.yaml --name hw1

kind load docker-image hw1-app:v1 --name hw1
kind load docker-image hw1-app:v2 --name hw1

kubectl apply -f kubernetes/deployment.yaml -f kubernetes/service.yaml
kubectl rollout status deployment/hw1-app-deployment
```

Проверка (то же показано в видео в разделе «Запуск»):

```bash
kubectl get pods -o wide
kubectl get endpointslices -l kubernetes.io/service-name=hw1-app-service
```

Должно быть три Pod `1/1 Running`, три адреса в EndpointSlice.

## Версии

- OS ➜  EndeavourOS x86_64
- Host ➜  21J8 (ThinkBook 16p G4 IRH)
- Kernel ➜  Linux 6.18.48-1-lts


- Docker version 29.9.0, build f415da838b
- Client version: kubectl v1.37.1, Kustomize Version: v5.8.1
- kind v0.33.0 go1.27.0-X:nodwarf5 linux/amd64
- Java 21, Spring Boot 4.1.1 (сборка идёт внутри Docker)

## Сценарий 1. Удаление Pod

Команды:

```bash
./load/monitor-ready.sh evidence/s1_monitor-ready.log
kubectl get pods -w
./load/load-test.sh evidence/s1_delete.log
kubectl delete pod $POD_NAME
```

`monitor-ready.sh` пишет число Ready-реплик.
`kubectl get pods -w` запускает просмотр состояния подов в реальном времени.
`load-test.sh` создаёт нагрузку через Service: запросы идут по очереди, каждый новым `curl`, таймаут 0.02 c. В конце он печатает итог.
`kubectl delete pod $POD_NAME` удаляет указанный под 

Замеры (`evidence/s1_delete.log`): 1500 запросов за 45 с (~33,3 requests per sec). Ошибок нет: ни таймаутов, ни отказов соединения, ни ошибок. Все ответы v1.

По Pod запросы распределились так: 325 / 565 / 560 / 50. Имён четыре, потому что к трём исходным Pod добавилась замена. Удалённый Pod ответил до удаления, а замена - после того, как стала Ready.

Выводы:
- Пока удаляли Pod, ни один запрос не упал. Удаляемый Pod перестаёт получать трафик и спокойно доделывает текущие запросы, а две другие реплики работают без перерыва. Для этого настроены preStop (5 с) и graceful shutdown
- Замена стала Ready, пока стартует Java. Всё это время сервис работал на 2/3 мощности, и каждая оставшаяся реплика получала в полтора раза больше запросов.

## Сценарий 2. Pod в not-ready

Команды:

```bash
./load/monitor-ready.sh evidence/s2_monitor-ready.log
kubectl get pods -w
./load/load-test.sh evidence/s2_unready.log
kubectl exec $POD_NAME -- curl -s -X POST http://localhost:8080/unready
kubectl exec $POD_NAME -- curl -s -X POST http://localhost:8080/ready-on
```
`monitor-ready.sh` пишет число Ready-реплик.
`kubectl get pods -w` запускает просмотр состояния подов в реальном времени.
`load-test.sh` создаёт нагрузку через Service: запросы идут по очереди, каждый новым `curl`, таймаут 0.02 c. В конце он печатает итог.
`kubectl exec $POD_NAME -- curl -s -X POST http://localhost:8080/unready` переводит указанный под в состояние not-ready
`kubectl exec $POD_NAME -- curl -s -X POST http://localhost:8080/ready-on` переводит указанный под в состояние ready

Замеры (`evidence/s2_unready.log`): 1500 запросов за 44 с (~34,09 requests per sec). Ошибок нет: ни таймаутов, ни отказов соединения, ни ошибок. Все ответы v1.
- По Pod запросы распределились так: 301 / 581 / 618. пока Pod был not-ready, остальные два отвечали без ошибок (во всём прогоне ошибок 0).


Параметры проб и почему такие:
- readiness: `/ready`, период 3 с, таймаут 1 с, 2 неудачи подряд - Pod выпадает из трафика за ~6-7 с. Исключение из Service дешёвое (Pod не перезапускается), поэтому проба частая.
- liveness: `/live`, период 5 с, таймаут 1 с, 3 неудачи, initialDelay 10 с - перезапуск дорогой , поэтому проба реже и мягче. Она проверяет только, что процесс жив.

Выводы:
- Pod, у которого `/ready` стал отвечать 503, перестал получать запросы. Это видно по 301 запросам против 581 / 618 у остальных.
- `/live` при этом продолжал отвечать 200, поэтому Kubernetes контейнер не перезапускал (RESTARTS = 0).
- Из трафика Pod выпал не сразу. После `/unready` он ещё ~6-7 с получал запросы, пока readiness-проба не получила 503 два раза подряд.

## Сценарий 3. Обновление v1 → v2

Команды:

```bash
./load/monitor-ready.sh evidence/s3_monitor-ready.log
kubectl get pods -w
./load/load-test.sh evidence/s3_update.log
kubectl set image deployment/hw1-app-deployment app=hw1-app:v2
```
`monitor-ready.sh` пишет число Ready-реплик.
`kubectl get pods -w` запускает просмотр состояния подов в реальном времени.
`load-test.sh` создаёт нагрузку через Service: запросы идут по очереди, каждый новым `curl`, таймаут 0.02 c. В конце он печатает итог.

`kubectl set image deployment/hw1-app-deployment app=hw1-app:v2` подменяет версию образа реплики в deployment манифесте.

Замеры (`evidence/s3_update.log`): 1500 запросов за 45 с (~~33,3 requests per sec). Ошибок нет: ни таймаутов, ни отказов соединения, ни ошибок.
Заменяются все три Pod, по одному: новый Pod запускается, и только когда он стал Ready, старый уходит в Terminating.
Все ответы  разделены по версиям v1: 848 / v2: 652.

Поставил `maxUnavailable: 0` и `maxSurge: 1`, это означает, что готовых Pod всё время не меньше трёх, а всего временно четыре. Цена такого выбора - нужен запас ресурсов на четвёртый Pod, и обновление идёт по одному Pod, то есть дольше.

Выводы:
- Все 1500 запросов получили 200: ни таймаутов, ни отказов соединения, ни ошибок.
- Пока шло обновление, одновременно отвечали обе версии, поэтому они должны быть совместимы между собой.

## Границы гарантий

1. Узел один (он же control plane), поэтому если он откажет, пропадут сразу все три реплики. Несколько реплик на одном узле от этого не защищают. Нужны несколько узлов и реплики на разных узлах.
2. Если новая версия не стартует, rollout встанет, а старые Pod продолжат отвечать (`maxUnavailable: 0`). Но если ошибка есть в обеих версиях или `/ready` сразу на всех Pod начнёт отвечать 503, Kubernetes не поможет: у Service не останется endpoints.
3. Пробы смотрят только на ответы `/live` и `/ready`, а не на то, правильно ли работает приложение. У меня `/ready` зависит от флага, а `GET /` этот флаг не проверяет. Поэтому Pod ещё несколько секунд получал запросы после `/unready`, пока проба не сработала.
4. PodDisruptionBudget защищает только от eviction, а `kubectl delete pod` его обходит. PDB я не создавал. Удаление в сценарии 1 прошло без ошибок не из-за защиты: Deployment пересоздаёт Pod, а остальные реплики продолжают отвечать.
5. `terminationGracePeriodSeconds` - 20 с, это лимит на остановку: `preStop` занимает 5 с, остановка Spring до ~15 с. Если приложение не успеет, Kubernetes убьёт его через SIGKILL, и запросы в работе оборвутся. `preStop` нужен, чтобы адрес успел исчезнуть из Service до SIGTERM.