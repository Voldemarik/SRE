name: CI

on:
  push:
    branches: [ "hw2" ]
  pull_request:
    branches: [ "hw2" ]

jobs:
  test-and-build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
      
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      
      - name: Unit tests
        run: mvn -B test
        working-directory: homework/hw2/app
      
      - name: Build Docker Image
        run: docker build --build-arg VERSION=${{ github.sha }} -t hw2-app:${{ github.sha }} homework/hw2/app