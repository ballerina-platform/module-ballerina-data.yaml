// Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

type OpenRecord record {};

type SimpleYaml record {|
    string name;
    int age;
    string description;
|};

type Employee record {|
    string name;
    int age;
    string department;
    Project[] projects;
|};

type Project record {|
    string name;
    string status;
|};

type BookCover record {|
    string title;
    int pages;
    string[] authors;
    float price;
|};

type RecordWithMapType record {|
    string name;
    map<string> data;
|};

type Author record {|
    string name;
    string birthdate;
    string hometown;
    boolean...;
|};

type Publisher record {|
    string name;
    int year;
    string...;
|};

type Book record {|
    string title;
    Author author;
    Publisher publisher;
    float...;
|};

type TicketBooking record {|
    string event;
    float price;
    record {|string...;|} attendee;
|};

type LibraryB record {
    [BookA, BookA] books;
};

type LibraryC record {|
    [BookA, BookA...] books;
|};


type BookA record {|
    string title;
    string author;
|};

type Singleton1 1;

type SingletonUnion Singleton1|2|"3";

type SingletonInRecord record {|
    Singleton1 value;
    SingletonUnion id;
|};
