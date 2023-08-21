/*
 * Copyright 2019-2020 Google LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */

package model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

@Entity
public class Coffee {

  @Id
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "coffee_sequence"
  )
  @GenericGenerator(
      name = "coffee_sequence",
      strategy = "com.google.cloud.spanner.hibernate.BatchedBitReversedSequenceStyleGenerator",
      parameters = {
          @Parameter(name = "sequence_name", value = "coffee_id"),
          @Parameter(name = "fetch_size", value = "200")
      }
  )
  private Long id;

  private String size;

  @ManyToOne
  private Customer customer;

  // Empty default constructor for Spring Data JPA.
  public Coffee() {
  }

  public Coffee(Customer customer, String size) {
    this.customer = customer;
    this.size = size;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getSize() {
    return size;
  }

  public void setSize(String size) {
    this.size = size;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }
}
