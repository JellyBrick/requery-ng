/*
 * Copyright 2016 requery.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.requery.test.jpa;


import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import java.io.Serializable;

@Entity
public interface Address extends Serializable {

    @Id
    @GeneratedValue
    int getId();

    String getLine1();
    String getLine2();
    String getState();

    @Embedded
    Coordinate getCoordinate();

    @Column(length = 5)
    String getZip();

    @Column(length = 2)
    String getCountry();

    String getCity();

    @OneToOne(mappedBy = "address")
    Person getPerson();

    AddressType getType();
}
